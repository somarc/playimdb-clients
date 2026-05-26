'use strict';

const IMDB_SUGGEST_BASE = 'https://v2.sg.media-imdb.com/suggestion/';
const PLAY_BASE = 'https://playimdb.com/title/';
const FETCH_TIMEOUT_MS = 8000;

const TYPE_LABELS = {
  feature: 'Movie',
  short: 'Short',
  tv: 'TV Series',
  tvSeries: 'TV Series',
  tvMiniSeries: 'Mini-Series',
  tvMovie: 'TV Movie',
  tvSpecial: 'TV Special',
  videoGame: 'Game',
  video: 'Video',
  podcastSeries: 'Podcast',
  podcastEpisode: 'Podcast Ep.',
};

let debounceTimer = null;
let activeController = null;
let selectedIndex = -1;

const searchInput = document.getElementById('search-input');
const searchBtn = document.getElementById('search-btn');
const resultsList = document.getElementById('results');
const statusEl = document.getElementById('status');

function showStatus(msg, isError = false) {
  statusEl.textContent = '';
  statusEl.className = 'status' + (isError ? ' error' : '');
  if (msg === 'loading') {
    statusEl.innerHTML = `Searching <span class="loading-dots"><span></span><span></span><span></span></span>`;
  } else {
    statusEl.textContent = msg;
  }
}

function hideStatus() {
  statusEl.className = 'status hidden';
  statusEl.textContent = '';
}

function clearResults() {
  resultsList.innerHTML = '';
  selectedIndex = -1;
}

function titleId(result) {
  return result.id || '';
}

function isTitle(result) {
  return result.id && result.id.startsWith('tt');
}

function buildPlayUrl(id) {
  return PLAY_BASE + id;
}

function resultItems() {
  return Array.from(resultsList.querySelectorAll('.result-item'));
}

function updateSelectedResult(nextIndex) {
  const items = resultItems();
  if (items.length === 0) {
    selectedIndex = -1;
    return;
  }

  selectedIndex = Math.max(0, Math.min(nextIndex, items.length - 1));
  items.forEach((item, index) => {
    const selected = index === selectedIndex;
    item.classList.toggle('selected', selected);
    item.setAttribute('aria-selected', selected ? 'true' : 'false');
    if (selected) {
      item.scrollIntoView({ block: 'nearest' });
    }
  });
}

function openSelectedResult() {
  const items = resultItems();
  if (selectedIndex < 0 || selectedIndex >= items.length) return false;
  items[selectedIndex].click();
  return true;
}

function typeLabel(result) {
  if (result.qid) return TYPE_LABELS[result.qid] || result.qid;
  return '';
}

function posterEl(result) {
  if (result.i && result.i.imageUrl) {
    const img = document.createElement('img');
    img.className = 'result-poster';
    img.src = result.i.imageUrl;
    img.alt = result.l || '';
    img.loading = 'lazy';
    img.onerror = () => {
      const placeholder = document.createElement('div');
      placeholder.className = 'result-poster-placeholder';
      placeholder.textContent = '🎬';
      img.replaceWith(placeholder);
    };
    return img;
  }
  const placeholder = document.createElement('div');
  placeholder.className = 'result-poster-placeholder';
  placeholder.textContent = '🎬';
  return placeholder;
}

function renderResults(suggestions) {
  clearResults();

  const titles = suggestions.filter(isTitle);

  if (titles.length === 0) {
    showStatus('No titles found.');
    return;
  }

  hideStatus();

  titles.forEach((result) => {
    const id = titleId(result);
    const playUrl = buildPlayUrl(id);

    const li = document.createElement('li');

    const a = document.createElement('a');
    a.className = 'result-item';
    a.href = playUrl;
    a.title = `Open ${result.l || id} on PlayIMDB`;
    a.setAttribute('role', 'option');
    a.setAttribute('aria-selected', 'false');

    a.addEventListener('click', (e) => {
      e.preventDefault();
      chrome.tabs.create({ url: playUrl });
      window.close();
    });

    a.appendChild(posterEl(result));

    const info = document.createElement('div');
    info.className = 'result-info';

    const titleDiv = document.createElement('div');
    titleDiv.className = 'result-title';
    titleDiv.textContent = result.l || id;
    info.appendChild(titleDiv);

    const meta = document.createElement('div');
    meta.className = 'result-meta';

    if (result.y) {
      const year = document.createElement('span');
      year.className = 'result-year';
      year.textContent = result.yr || result.y;
      meta.appendChild(year);
    }

    const type = typeLabel(result);
    if (type) {
      const badge = document.createElement('span');
      badge.className = 'result-type';
      badge.textContent = type;
      meta.appendChild(badge);
    }

    info.appendChild(meta);

    const urlLine = document.createElement('div');
    urlLine.className = 'result-url';
    urlLine.textContent = playUrl;
    info.appendChild(urlLine);

    a.appendChild(info);

    const arrow = document.createElement('span');
    arrow.className = 'result-arrow';
    arrow.textContent = '→';
    a.appendChild(arrow);

    li.appendChild(a);
    resultsList.appendChild(li);
  });

  updateSelectedResult(0);
}

async function fetchSuggestions(query) {
  const q = query.trim();
  if (!q) return;

  if (activeController) {
    activeController.superseded = true;
    activeController.abort();
  }
  const controller = new AbortController();
  controller.superseded = false;
  controller.timedOut = false;
  activeController = controller;

  const firstChar = q[0].toLowerCase();
  const encodedQuery = encodeURIComponent(q);
  const url = `${IMDB_SUGGEST_BASE}${firstChar}/${encodedQuery}.json`;

  showStatus('loading');
  clearResults();

  const timer = setTimeout(() => {
    controller.timedOut = true;
    controller.abort();
  }, FETCH_TIMEOUT_MS);

  try {
    const resp = await fetch(url, {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
      signal: controller.signal,
    });

    if (controller.signal.aborted) return;

    if (!resp.ok) {
      showStatus('Could not reach IMDB. Try again.', true);
      return;
    }

    const data = await resp.json();
    if (controller.signal.aborted) return;

    const suggestions = data.d || [];
    renderResults(suggestions);
  } catch (err) {
    if (err.name === 'AbortError') {
      if (activeController === controller && controller.timedOut && !controller.superseded) {
        showStatus('Search timed out. Try again.', true);
      }
      return;
    }
    showStatus('Search failed. Check your connection.', true);
  } finally {
    clearTimeout(timer);
    if (activeController === controller) {
      activeController = null;
    }
  }
}

function onSearch() {
  const q = searchInput.value.trim();
  if (!q) {
    clearResults();
    hideStatus();
    return;
  }
  fetchSuggestions(q);
}

searchInput.addEventListener('input', () => {
  clearTimeout(debounceTimer);
  const q = searchInput.value.trim();
  if (!q) {
    clearResults();
    hideStatus();
    return;
  }
  debounceTimer = setTimeout(onSearch, 350);
});

searchInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    clearTimeout(debounceTimer);
    if (openSelectedResult()) {
      e.preventDefault();
      return;
    }
    onSearch();
  } else if (e.key === 'ArrowDown') {
    e.preventDefault();
    updateSelectedResult(selectedIndex < 0 ? 0 : selectedIndex + 1);
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    updateSelectedResult(selectedIndex <= 0 ? 0 : selectedIndex - 1);
  }
});

searchBtn.addEventListener('click', () => {
  clearTimeout(debounceTimer);
  onSearch();
});

searchInput.focus();
