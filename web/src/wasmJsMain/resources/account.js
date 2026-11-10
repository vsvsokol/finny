/* Нативные формы оставляют ввод пароля доступным браузеру и менеджеру паролей. */
(() => {
  'use strict';
  let send, state = {}, opened = false, mode = 'login', dismissedFirst = false;
  const bar = document.createElement('header'); bar.id = 'finny-account-bar';
  const status = document.createElement('span'); status.setAttribute('role', 'status');
  const open = document.createElement('button'); open.type = 'button'; open.textContent = 'Сохранения';
  open.disabled = true;
  open.onclick = () => { opened = true; render(); panel.querySelector('input')?.focus(); };
  bar.append(status, open); document.body.append(bar);
  const overlay = document.createElement('section'); overlay.id = 'finny-account'; overlay.hidden = true;
  overlay.setAttribute('aria-label', 'Учётная запись и сохранения');
  overlay.setAttribute('role', 'dialog'); overlay.setAttribute('aria-modal', 'true');
  const panel = document.createElement('div'); panel.className = 'account-panel';
  const title = document.createElement('h1'); title.textContent = 'Сохранения Финни';
  const intro = document.createElement('p');
  const error = document.createElement('p'); error.className = 'account-error'; error.setAttribute('role', 'alert');
  const content = document.createElement('div');
  const close = button('Вернуться в игру', () => {
    opened = false; dismissedFirst = true; send('guest', ''); render();
  }, true);
  panel.append(title, intro, error, content, close); overlay.append(panel); document.body.append(overlay);
  let contentKey = '';
  const offline = document.createElement('span'); offline.className = 'offline-state';
  offline.setAttribute('role', 'status'); bar.insertBefore(offline, open);
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').then(() => navigator.serviceWorker.ready).then(() => {
      offline.textContent = 'Офлайн ✓'; offline.title = 'Файлы игры сохранены для открытия без сети';
    }).catch(() => { offline.textContent = ''; });
  }
  function button(text, callback, quiet = false) {
    const b = document.createElement('button'); b.type = 'button'; b.textContent = text;
    b.className = quiet ? 'quiet' : ''; b.onclick = callback; return b;
  }
  function action(name, data = {}) { send(name, JSON.stringify(data)); }
  function field(form, caption, name, type = 'text', autocomplete = '') {
    const label = document.createElement('label'); label.textContent = caption;
    const input = document.createElement('input'); input.name = name; input.type = type;
    input.autocomplete = autocomplete; input.required = true;
    input.maxLength = name.toLowerCase().includes('password') ? 128 : name === 'login' ? 32 : 128;
    if (name === 'login') { input.pattern = '[a-zA-Z0-9_.-]{3,32}'; input.autocapitalize = 'none'; input.spellcheck = false; }
    if (name === 'password' || name === 'newPassword') input.minLength = 10;
    label.append(input); form.append(label); return input;
  }
  function authForm() {
    const form = document.createElement('form');
    if (!['password', 'recovery-code'].includes(mode)) {
      const login = field(form, 'Логин (латинские буквы, цифры, _ . -)', 'login', 'text', 'username');
      login.value = state.login || '';
    }
    if (mode === 'recover') field(form, 'Код восстановления', 'recoveryCode', 'text', 'off');
    if (mode === 'password') field(form, 'Текущий пароль', 'currentPassword', 'password', 'current-password');
    field(form, mode === 'recover' || mode === 'password' ? 'Новый пароль (от 10 символов)' : 'Пароль (от 10 символов)',
      mode === 'recover' || mode === 'password' ? 'newPassword' : 'password', 'password',
      mode === 'register' || mode === 'recover' || mode === 'password' ? 'new-password' : 'current-password');
    const submit = document.createElement('button'); submit.type = 'submit';
    submit.textContent = ({login:'Войти', reauth:'Войти', register:'Создать учётную запись', recover:'Восстановить доступ',
      password:'Сменить пароль', 'recovery-code':'Получить новый код'})[mode];
    form.append(submit);
    form.onsubmit = event => {
      event.preventDefault(); if (state.busy) return;
      const data = Object.fromEntries(new FormData(form));
      form.querySelectorAll('input[type=password]').forEach(input => { input.value = ''; });
      action(mode === 'reauth' ? 'login' : mode, data);
    };
    content.append(form);
    if (!state.login) {
      for (const [name, text] of [['login','Уже есть учётная запись'], ['register','Создать учётную запись'], ['recover','Забыли пароль?']]) {
        if (mode !== name) content.append(button(text, () => { mode = name; contentKey = ''; render(); }, true));
      }
      const note = document.createElement('p');
      note.textContent = 'Почта и настоящее имя не нужны. Придумайте логин-псевдоним. Код восстановления сохраните вместе со взрослым.';
      content.append(note);
    } else content.append(button('Назад к сохранениям', () => { mode = 'login'; contentKey = ''; render(); }, true));
  }
  function accountView() {
    const who = document.createElement('p'); who.textContent = 'Учётная запись: ' + state.login; content.append(who);
    if (state.needsLogin) content.append(button('Войти снова', () => { mode = 'reauth'; contentKey = ''; render(); }));
    if (state.needsLogin) content.append(button('Забыли пароль?', () => { mode = 'recover'; contentKey = ''; render(); }, true));
    if (state.guestImport) content.append(button('Перенести гостевых питомцев', () => action('import')));
    for (const conflict of state.conflicts || []) {
      const box = document.createElement('section'); box.className = 'account-conflict';
      const heading = document.createElement('h2'); heading.textContent = conflict.name;
      const text = document.createElement('p');
      text.textContent = 'Этот профиль изменился на другом устройстве. Можно взять серверную версию или сохранить местную отдельным питомцем. Перед выбором обе версии попадут в резервную копию этого браузера.';
      box.append(heading, text,
        button('Сохранить обе версии', () => send('both', String(conflict.id))),
        button('Взять серверную версию', () => send('server', String(conflict.id)), true));
      content.append(box);
    }
    content.append(button('Скачать местную резервную копию', () => send('export', ''), true));
    content.append(button('Сменить пароль', () => { mode = 'password'; contentKey = ''; render(); }, true));
    content.append(button('Новый код восстановления', () => { mode = 'recovery-code'; contentKey = ''; render(); }, true));
    content.append(button('Выйти из учётной записи', () => action('logout'), true));
    const note = document.createElement('p');
    note.textContent = 'Неотправленные изменения останутся в этом браузере для следующего входа в ту же учётную запись. Демо-питомцы на сервер не отправляются.';
    content.append(note);
  }
  function render() {
    const nextStatus = state.status || 'Сохранения Финни';
    if (status.textContent !== nextStatus) status.textContent = nextStatus;
    open.textContent = state.login ? 'Учётная запись' : 'Войти / сохранения';
    if (state.firstVisit && !dismissedFirst) opened = true;
    if (state.recoveryCode || state.fatal) opened = true;
    overlay.hidden = !opened;
    document.getElementById('finny-game').inert = opened;
    open.setAttribute('aria-expanded', String(opened));
    intro.textContent = state.login ? 'Ваши питомцы и прогресс сохраняются на сервере.' : 'Войдите, чтобы открыть питомцев на другом устройстве. Можно играть и без входа — прогресс останется в этом браузере.';
    const nextError = state.fatal || state.error || '';
    if (error.textContent !== nextError) error.textContent = nextError;
    close.hidden = !!state.recoveryCode || !!state.fatal;
    const key = JSON.stringify([mode, state.login, state.recoveryCode, state.guestImport, state.conflicts, state.needsLogin]);
    if (key !== contentKey) {
      contentKey = key; content.replaceChildren();
      if (state.recoveryCode) {
        const text = document.createElement('p'); text.textContent = 'Сохраните код: он поможет вернуть доступ, если пароль забудется. Сервер больше не покажет этот код.';
        const code = document.createElement('input'); code.readOnly = true; code.value = state.recoveryCode;
        code.setAttribute('aria-label', 'Код восстановления');
        content.append(text, code, button('Я сохранил код', () => send('recovery-saved', '')));
      } else if (state.login && mode === 'login') accountView();
      else authForm();
    }
    panel.querySelectorAll('button,input').forEach(element => { element.disabled = !!state.busy || !!state.fatal; });
    open.disabled = !!state.busy;
  }
  globalThis.finnyAccount = {
    start(callback) { send = callback; },
    render(next) { state = next; render(); }
  };
})();
