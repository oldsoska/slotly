'use strict';

// Public booking page for /b/{slug}. Talks only to /api/v1/public/** and never renders
// server data as HTML (textContent everywhere), so names cannot inject markup.

const API = '/api/v1/public';
const slug = decodeURIComponent(location.pathname.split('/')[2] || '');

const state = {
  business: null,
  serviceId: null,
  slot: null,
};

const $ = (id) => document.getElementById(id);

async function request(path, options = {}) {
  const response = await fetch(API + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (response.status === 429) {
    const seconds = Number(response.headers.get('Retry-After')) || 60;
    throw new Error('Слишком много попыток. Попробуйте снова через ' + Math.ceil(seconds / 60) + ' мин.');
  }
  if (!response.ok) {
    throw new Error((body && body.detail) || 'Не удалось выполнить запрос (' + response.status + ')');
  }
  return body;
}

function showError(message) {
  $('error').textContent = message;
  $('error').hidden = !message;
}

function zone() {
  return state.business ? state.business.timezone : 'UTC';
}

function formatTime(instant, timeZone = zone()) {
  return new Intl.DateTimeFormat('ru-RU', { timeZone, hour: '2-digit', minute: '2-digit' }).format(new Date(instant));
}

function formatDateTime(instant, timeZone) {
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone, weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit',
  }).format(new Date(instant));
}

/** Today's date in the business's timezone, as YYYY-MM-DD (the en-CA locale formats dates that way). */
function todayInBusinessZone() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: zone() }).format(new Date());
}

function formatPrice(price) {
  return new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 }).format(price);
}

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  Object.entries(attrs).forEach(([key, value]) => node.setAttribute(key, value));
  children.forEach((child) => node.append(child));
  return node;
}

function select(container, button) {
  container.querySelectorAll('[aria-pressed]').forEach((b) => b.setAttribute('aria-pressed', 'false'));
  button.setAttribute('aria-pressed', 'true');
}

function renderBusiness() {
  const { business } = state;
  document.title = 'Запись — ' + business.name;
  $('business-name').textContent = business.name;

  const services = $('services');
  services.replaceChildren();
  if (business.services.length === 0) {
    services.append(el('p', { class: 'muted' }, 'Услуги пока не добавлены.'));
  }
  business.services.forEach((service) => {
    const button = el('button', { type: 'button', class: 'option', 'aria-pressed': 'false' },
      el('span', {}, service.name),
      el('small', {}, service.durationMinutes + ' мин · ' + formatPrice(service.price)));
    button.addEventListener('click', () => {
      select(services, button);
      state.serviceId = service.id;
      loadSlots();
    });
    services.append(button);
  });

  const staff = $('staff');
  staff.replaceChildren(el('option', { value: '' }, 'Любой мастер'));
  business.staff.forEach((master) => staff.append(el('option', { value: String(master.id) }, master.fullName)));

  const today = todayInBusinessZone();
  $('date').min = today;
  $('date').value = today;

  $('booking-flow').hidden = false;
}

/**
 * The API lists slots per staff member. Sorted by time for display; with "any master" each start
 * time is offered once and goes to the first master who is free then.
 */
function byStartTime(slots, onePerTime) {
  const sorted = [...slots].sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  if (!onePerTime) return sorted;
  const seen = new Set();
  return sorted.filter((slot) => !seen.has(slot.startsAt) && seen.add(slot.startsAt));
}

async function loadSlots() {
  state.slot = null;
  $('client-form').hidden = true;
  const slots = $('slots');
  slots.replaceChildren();
  $('slots-empty').hidden = true;
  if (!state.serviceId || !$('date').value) {
    slots.append(el('p', { class: 'muted' }, 'Выберите услугу и дату.'));
    return;
  }

  const params = new URLSearchParams({ serviceId: state.serviceId, date: $('date').value });
  if ($('staff').value) params.set('staffId', $('staff').value);
  try {
    showError('');
    const free = await request('/businesses/' + encodeURIComponent(slug) + '/availability?' + params);
    $('slots-empty').hidden = free.length > 0;
    const showMaster = !$('staff').value;
    byStartTime(free, showMaster).forEach((slot) => {
      const button = el('button', { type: 'button', class: 'slot', 'aria-pressed': 'false' }, formatTime(slot.startsAt));
      if (showMaster) button.append(el('small', {}, slot.staffName));
      button.addEventListener('click', () => {
        select(slots, button);
        state.slot = slot;
        $('chosen').textContent = formatDateTime(slot.startsAt, zone()) + ' · ' + slot.staffName;
        $('client-form').hidden = false;
        $('client-name').focus();
      });
      slots.append(button);
    });
  } catch (e) {
    showError(e.message);
  }
}

async function submitBooking(event) {
  event.preventDefault();
  const submit = event.submitter;
  submit.disabled = true;
  try {
    showError('');
    const confirmation = await request('/businesses/' + encodeURIComponent(slug) + '/bookings', {
      method: 'POST',
      body: JSON.stringify({
        staffId: state.slot.staffId,
        serviceId: state.serviceId,
        clientName: $('client-name').value.trim(),
        clientPhone: $('client-phone').value.trim(),
        startsAt: state.slot.startsAt,
      }),
    });
    // Keep the token in the URL so the client can bookmark or reopen the confirmation
    history.replaceState(null, '', location.pathname + '?booking=' + confirmation.cancelToken);
    renderConfirmation(confirmation);
  } catch (e) {
    showError(e.message);
    loadSlots();
  } finally {
    submit.disabled = false;
  }
}

function renderConfirmation(c) {
  $('booking-flow').hidden = true;
  $('business-name').textContent = c.businessName;
  document.title = 'Запись — ' + c.businessName;
  $('c-service').textContent = c.serviceName;
  $('c-staff').textContent = c.staffName;
  $('c-when').textContent = formatDateTime(c.startsAt, c.timezone) + '–' + formatTime(c.endsAt, c.timezone);
  const cancelled = c.status === 'CANCELLED';
  $('confirmation-title').textContent = cancelled ? 'Запись отменена' : 'Вы записаны';
  $('cancel-booking').hidden = c.status !== 'CONFIRMED';
  $('c-hint').hidden = cancelled;
  $('book-again').href = '/b/' + encodeURIComponent(c.businessSlug);
  $('confirmation').hidden = false;

  $('cancel-booking').onclick = async () => {
    if (!confirm('Отменить запись?')) return;
    try {
      showError('');
      renderConfirmation(await request('/bookings/' + c.cancelToken + '/cancel', { method: 'POST' }));
    } catch (e) {
      showError(e.message);
    }
  };
}

async function init() {
  const token = new URLSearchParams(location.search).get('booking');
  try {
    if (token) {
      renderConfirmation(await request('/bookings/' + encodeURIComponent(token)));
      return;
    }
    state.business = await request('/businesses/' + encodeURIComponent(slug));
    renderBusiness();
    loadSlots();
  } catch (e) {
    $('business-name').textContent = 'Страница не найдена';
    showError(e.message);
  }
}

$('staff').addEventListener('change', loadSlots);
$('date').addEventListener('change', loadSlots);
$('client-form').addEventListener('submit', submitBooking);
init();
