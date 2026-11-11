/* Нативные формы оставляют ввод пароля доступным браузеру и менеджеру паролей. */
(() => {
  'use strict';
  let send, state = {}, opened = false, mode = 'login', dismissedFirst = false;
  let contentKey = '';

  const bar = document.createElement('header');
  bar.id = 'finny-account-bar';
  const brand = element('span', 'account-brand');
  brand.append(mascot(), 'Питомец Финни');
  const status = element('span', 'account-status');
  status.setAttribute('role', 'status');
  const open = button('Войти', () => {
    mode = 'login';
    opened = true;
    render();
  });
  open.disabled = true;
  open.setAttribute('aria-haspopup', 'dialog');
  open.setAttribute('aria-controls', 'finny-account');
  bar.append(brand, status, open);
  document.body.append(bar);

  const dialog = document.createElement('dialog');
  dialog.id = 'finny-account';
  dialog.setAttribute('aria-labelledby', 'finny-account-title');
  dialog.addEventListener('keydown', event => {
    if (event.key === 'Escape') dismiss(event);
  });
  dialog.addEventListener('cancel', dismiss);
  dialog.addEventListener('close', () => {
    if (!opened || dialog.open) return;
    if (canClose()) closeAccount();
    else render();
  });

  function dismiss(event) {
    event.preventDefault();
    if (canClose()) closeAccount();
  }

  function canClose() {
    return !state.busy && !state.recoveryCode && !state.fatal;
  }

  const layout = element('div', 'account-layout');
  const art = element('div', 'account-art');
  art.append(mascot());
  const panel = element('div', 'account-panel');
  const title = document.createElement('h1');
  title.id = 'finny-account-title';
  title.tabIndex = -1;
  const intro = element('p', 'account-intro');
  const error = element('p', 'account-error');
  error.setAttribute('role', 'alert');
  const content = document.createElement('div');
  const footer = element('footer', 'account-footer');
  const close = button('К игре', closeAccount, true);
  footer.append(close);
  panel.append(title, intro, error, content, footer);
  layout.append(art, panel);
  dialog.append(layout);
  document.body.append(dialog);

  const offline = document.createElement('p');
  offline.hidden = true;
  offline.setAttribute('role', 'status');
  offline.textContent = '✓ Игра доступна без сети';
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').then(() => navigator.serviceWorker.ready).then(() => {
      offline.hidden = false;
    }).catch(() => { offline.hidden = true; });
  }

  const titles = {
    login: 'Вход в игру',
    reauth: 'Повторный вход',
    register: 'Создать аккаунт',
    recover: 'Восстановить доступ',
    password: 'Сменить пароль',
    'recovery-code': 'Новый код восстановления'
  };

  function element(tag, className) {
    const node = document.createElement(tag);
    node.className = className;
    return node;
  }

  function mascot() {
    const image = document.createElement('img');
    image.src = 'favicon.png';
    image.alt = '';
    image.width = 523;
    image.height = 323;
    return image;
  }

  function button(text, callback, quiet = false) {
    const node = element('button', quiet ? 'account-button quiet' : 'account-button');
    node.type = 'button';
    node.textContent = text;
    node.onclick = callback;
    return node;
  }

  function paragraph(text) {
    const node = document.createElement('p');
    node.textContent = text;
    return node;
  }

  function closeAccount() {
    opened = false;
    dismissedFirst = true;
    send('guest', '');
    render();
    open.focus();
  }

  function changeMode(next) {
    mode = next;
    render();
    dialog.scrollTop = 0;
    title.focus({ preventScroll: true });
  }

  function action(name, data = {}) {
    send(name, JSON.stringify(data));
  }

  function field(form, caption, name, type, autocomplete, hint) {
    const label = document.createElement('label');
    const captionText = document.createElement('span');
    captionText.textContent = caption;
    label.append(captionText);
    const input = document.createElement('input');
    input.name = name;
    input.type = type;
    input.autocomplete = autocomplete;
    input.required = true;
    input.maxLength = name.toLowerCase().includes('password') ? 128 : name === 'login' ? 32 : 128;
    if (name === 'login') {
      input.pattern = '[a-zA-Z0-9_.\\-]{3,32}';
      input.autocapitalize = 'none';
      input.spellcheck = false;
    }
    if (name === 'password' || name === 'newPassword') input.minLength = 10;
    label.append(input);
    if (hint) {
      const description = document.createElement('small');
      description.id = 'account-hint-' + name;
      description.textContent = hint;
      input.setAttribute('aria-describedby', description.id);
      label.append(description);
    }
    form.append(label);
    return input;
  }

  function authForm() {
    const form = document.createElement('form');
    if (!['password', 'recovery-code'].includes(mode)) {
      const hint = mode === 'register' ? '3–32 символа: латинские буквы, цифры, точка, дефис и подчёркивание.' : '';
      const login = field(form, 'Логин', 'login', 'text', 'username', hint);
      login.value = state.login || '';
    }
    if (mode === 'recover') field(form, 'Код восстановления', 'recoveryCode', 'text', 'off');
    if (mode === 'password') field(form, 'Текущий пароль', 'currentPassword', 'password', 'current-password');
    const replacingPassword = mode === 'recover' || mode === 'password';
    const creatingPassword = replacingPassword || mode === 'register';
    field(form, replacingPassword ? 'Новый пароль' : 'Пароль',
      replacingPassword ? 'newPassword' : 'password', 'password',
      creatingPassword ? 'new-password' : 'current-password',
      creatingPassword ? 'Не меньше 10 символов.' : '');

    const submit = element('button', 'account-button');
    submit.type = 'submit';
    submit.textContent = ({
      login: 'Войти', reauth: 'Войти', register: 'Создать аккаунт', recover: 'Восстановить доступ',
      password: 'Сменить пароль', 'recovery-code': 'Получить новый код'
    })[mode];
    form.append(submit);
    form.onsubmit = event => {
      event.preventDefault();
      if (state.busy) return;
      const data = Object.fromEntries(new FormData(form));
      form.querySelectorAll('input[type=password]').forEach(input => { input.value = ''; });
      action(mode === 'reauth' ? 'login' : mode, data);
    };

    const actions = element('div', 'account-actions');
    if (!state.login && mode === 'login') {
      actions.append(button('Создать аккаунт', () => changeMode('register'), true),
        button('Забыли пароль?', () => changeMode('recover'), true));
    } else {
      actions.append(button(state.login ? 'Назад в аккаунт' : 'Назад ко входу', () => changeMode('login'), true));
      if (mode === 'reauth') actions.append(button('Забыли пароль?', () => changeMode('recover'), true));
    }
    content.append(form, actions);
    if (!state.login && mode === 'login') content.append(offline);
  }

  function section(heading, className = 'account-section') {
    const box = element('section', className);
    const title = document.createElement('h2');
    title.textContent = heading;
    box.append(title);
    return box;
  }

  function accountView() {
    const sections = element('div', 'account-sections');
    const saves = section('Сохранения');
    saves.append(offline);
    if (state.guestImport) saves.append(button('Перенести гостевых питомцев', () => action('import')));
    saves.append(button('Скачать копию из браузера', () => send('export', ''), true));

    const access = section('Вход и пароль');
    if (state.needsLogin) {
      access.append(button('Войти снова', () => changeMode('reauth')),
        button('Забыли пароль?', () => changeMode('recover'), true));
    } else {
      access.append(button('Сменить пароль', () => changeMode('password'), true),
        button('Новый код восстановления', () => changeMode('recovery-code'), true));
    }
    access.append(button('Выйти из аккаунта', () => action('logout'), true));
    sections.append(saves, access);

    for (const conflict of state.conflicts || []) {
      const box = section(conflict.name, 'account-section account-conflict');
      box.append(
        paragraph('Этот питомец изменился на другом устройстве. Перед выбором обе версии сохранятся в копии этого браузера.'),
        button('Сохранить обе версии', () => send('both', String(conflict.id))),
        button('Взять версию с сервера', () => send('server', String(conflict.id)), true)
      );
      sections.append(box);
    }
    content.append(sections);
  }

  function recoveryView() {
    const box = element('div', 'account-recovery');
    const code = document.createElement('input');
    code.readOnly = true;
    code.value = state.recoveryCode;
    code.setAttribute('aria-label', 'Код восстановления');
    box.append(code, button('Я сохранил код', () => send('recovery-saved', '')));
    content.append(box);
  }

  function render() {
    const nextStatus = state.status || 'Сохранения Финни';
    if (status.textContent !== nextStatus) status.textContent = nextStatus;
    open.textContent = state.login ? 'Аккаунт' : 'Войти';
    if ((state.firstVisit && !dismissedFirst) || state.recoveryCode || state.fatal) opened = true;
    open.disabled = !send || !!state.busy;
    open.setAttribute('aria-expanded', String(opened));

    const settings = state.login && mode === 'login' && !state.recoveryCode && !state.fatal;
    dialog.dataset.view = settings ? 'settings' : 'form';
    title.textContent = state.fatal ? 'Сохранения Финни' :
      state.recoveryCode ? 'Код восстановления' : settings ? 'Аккаунт' : titles[mode];
    intro.textContent = state.fatal ? '' :
      state.recoveryCode ? 'Сохраните код вместе со взрослым. Он поможет вернуть доступ, если вы забудете пароль.' :
      settings ? state.login + ' · ' + nextStatus :
      mode === 'register' ? 'Придумайте логин. Почта и настоящее имя не нужны.' :
      mode === 'login' ? 'Войдите, чтобы играть с тем же питомцем на другом устройстве.' : '';
    intro.hidden = !intro.textContent;

    const nextError = state.fatal || state.error || '';
    if (error.textContent !== nextError) error.textContent = nextError;
    footer.hidden = !!state.recoveryCode || !!state.fatal;
    close.textContent = state.login ? 'К игре' : 'Играть без входа';

    const key = JSON.stringify([mode, state.login, state.recoveryCode, state.guestImport, state.conflicts, state.needsLogin, state.fatal]);
    if (key !== contentKey) {
      const focusWasInContent = content.contains(document.activeElement);
      contentKey = key;
      content.replaceChildren();
      if (!state.fatal) {
        if (state.recoveryCode) recoveryView();
        else if (settings) accountView();
        else authForm();
      }
      if (focusWasInContent) title.focus({ preventScroll: true });
    }
    panel.querySelectorAll('button,input').forEach(node => { node.disabled = !!state.busy; });
    if (opened && !dialog.open) {
      dialog.showModal();
      dialog.scrollTop = 0;
      title.focus({ preventScroll: true });
    } else if (!opened && dialog.open) {
      dialog.close();
    }
  }

  globalThis.finnyAccount = {
    start(callback) { send = callback; },
    render(next) { state = next; render(); }
  };
})();
