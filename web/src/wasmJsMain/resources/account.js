/* Нативные формы оставляют ввод пароля доступным браузеру и менеджеру паролей. */
(() => {
  'use strict';
  let send, state = {}, opened = false, mode = 'login', dismissedFirst = false;
  let contentKey = '', friendsKey = '', friendsList, friendLogin, returnFocus;

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
    if (mode === 'friends') send('friends-close', '');
    opened = false;
    dismissedFirst = true;
    if (state.firstVisit) send('guest', '');
    render();
    if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true });
    returnFocus = undefined;
  }

  function changeMode(next) {
    if (mode === 'friends' && next !== 'friends') send('friends-close', '');
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

  function friendsView() {
    if (!state.login || state.needsLogin) {
      content.append(button(state.login ? 'Войти снова' : 'Войти', () => changeMode(state.login ? 'reauth' : 'login')));
      return;
    }
    const form = element('form', 'friend-form');
    friendLogin = field(form, 'Ник друга', 'login', 'text', 'off');
    const submit = element('button', 'account-button');
    submit.type = 'submit';
    submit.textContent = 'Отправить запрос';
    form.append(submit);
    form.onsubmit = event => {
      event.preventDefault();
      if (!state.busy) action('friend-request', { login: friendLogin.value });
    };
    const notice = element('p', 'friends-notice');
    notice.setAttribute('role', 'status');
    friendsList = element('div', 'friends-list');
    content.append(form, notice, friendsList);
  }

  function renderFriends() {
    if (!friendsList || !content.contains(friendsList)) return;
    const notice = content.querySelector('.friends-notice');
    if (notice.textContent !== state.friendsNotice) {
      notice.textContent = state.friendsNotice || '';
      if (state.friendsNotice === 'Запрос отправлен.' || state.friendsNotice === 'Вы уже друзья.') friendLogin.value = '';
    }
    const key = JSON.stringify([state.friends, !!state.friendsError]);
    if (key === friendsKey) return;
    friendsKey = key;
    const listHadFocus = friendsList.contains(document.activeElement);
    friendsList.replaceChildren();
    if (!state.friends) {
      friendsList.append(state.friendsError
        ? button('Обновить список', () => send('friends-refresh', ''), true)
        : paragraph('Загружаем список…'));
    } else {
      const groups = [
        ['incoming', 'Входящие запросы', [['Принять', 'accept'], ['Отклонить', 'decline']]],
        ['outgoing', 'Отправленные запросы', [['Отменить запрос', 'cancel']]],
        ['friend', 'Мои друзья', [['Удалить', 'remove']]]
      ];
      for (const [status, heading, actions] of groups) {
        const rows = state.friends.filter(row => row.status === status);
        if (!rows.length && status !== 'friend') continue;
        const group = section(heading);
        if (!rows.length) group.append(paragraph('Здесь появятся друзья, когда вы примете запрос.'));
        else {
          const list = element('ul', 'friend-rows');
          for (const row of rows) {
            const item = element('li', 'friend-row');
            const login = element('span', 'friend-login');
            login.textContent = row.login;
            const controls = element('div', 'friend-controls');
            for (const [label, name] of actions) {
              const control = button(label, () => action('friend-change', { id: row.id, action: name }), name !== 'accept');
              control.setAttribute('aria-label', `${label} · ${row.login}`);
              controls.append(control);
            }
            item.append(login, controls);
            list.append(item);
          }
          group.append(list);
        }
        friendsList.append(group);
      }
    }
    if (listHadFocus) title.focus({ preventScroll: true });
  }

  function openAccount(view) {
    if (!send || opened || !canClose()) return;
    returnFocus = document.activeElement;
    mode = view;
    opened = true;
    send(view === 'friends' ? 'friends-open' : 'account-open', '');
    render();
  }

  function render() {
    const nextStatus = state.status || 'Сохранения Финни';
    if ((state.firstVisit && !dismissedFirst) || state.recoveryCode || state.fatal) opened = true;

    const settings = state.login && mode === 'login' && !state.recoveryCode && !state.fatal;
    const social = mode === 'friends' && !state.recoveryCode && !state.fatal;
    dialog.dataset.view = social ? 'friends' : settings ? 'settings' : 'form';
    const standalone = opened && !settings && !social;
    document.body.classList.toggle('account-screen', standalone);
    dialog.classList.toggle('account-standalone', standalone);
    title.textContent = state.fatal ? 'Сохранения Финни' :
      state.recoveryCode ? 'Код восстановления' : social ? 'Друзья' : settings ? 'Аккаунт' : titles[mode];
    intro.textContent = state.fatal ? '' :
      state.recoveryCode ? 'Сохраните код вместе со взрослым. Он поможет вернуть доступ, если вы забудете пароль.' :
      social ? (state.login && !state.needsLogin ? 'Ваш ник в игре — ' + state.login : 'Войдите в игру, чтобы добавлять друзей.') :
      settings ? state.login + ' · ' + nextStatus :
      mode === 'register' ? 'Придумайте логин. Почта и настоящее имя не нужны.' :
      mode === 'login' ? 'Войдите, чтобы играть с тем же питомцем на другом устройстве.' : '';
    intro.hidden = !intro.textContent;

    const nextError = state.fatal || (social ? state.friendsError : state.error) || '';
    if (error.textContent !== nextError) error.textContent = nextError;
    footer.hidden = !!state.recoveryCode || !!state.fatal;
    close.textContent = social || state.login ? 'К игре' : 'Играть без входа';

    const key = JSON.stringify([mode, state.login, state.recoveryCode, state.guestImport, state.conflicts, state.needsLogin, state.fatal]);
    if (key !== contentKey) {
      const focusWasInContent = content.contains(document.activeElement);
      contentKey = key;
      friendsKey = '';
      content.replaceChildren();
      if (!state.fatal) {
        if (state.recoveryCode) recoveryView();
        else if (social) friendsView();
        else if (settings) accountView();
        else authForm();
      }
      if (focusWasInContent) title.focus({ preventScroll: true });
    }
    if (social) renderFriends();
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
    open: openAccount,
    render(next) { state = next; render(); }
  };
})();
