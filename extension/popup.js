'use strict';

const IMDB_SUGGEST_BASE = 'https://v2.sg.media-imdb.com/suggestion/';
const IMDB_GRAPHQL_URL = 'https://caching.graphql.imdb.com/';
const PLAY_BASE = 'https://playimdb.com/title/';
const FETCH_TIMEOUT_MS = 8000;
const CHART_CACHE_TTL_MS = 24 * 60 * 60 * 1000;

const CHART_QUERY = `
  query Chart($first: Int!, $chartType: ChartTitleType!) {
    chartTitles(first: $first, chart: {chartType: $chartType}) {
      edges {
        node {
          id
          titleText { text }
          releaseYear { year }
          primaryImage { url }
          titleType { id text }
        }
      }
    }
  }
`;

const CHARTS = {
  'top250-movies': { label: 'Top Movies', chartType: 'TOP_RATED_MOVIES', limit: 250 },
  'top250-tv': { label: 'Top TV', chartType: 'TOP_RATED_TV_SHOWS', limit: 250 },
  'popular-movies': { label: 'Popular Movies', chartType: 'MOST_POPULAR_MOVIES', limit: 100 },
  'popular-tv': { label: 'Popular TV', chartType: 'MOST_POPULAR_TV_SHOWS', limit: 100 },
};

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
let mode = 'search';
let selectedChart = 'top250-movies';

const searchInput = document.getElementById('search-input');
const searchBtn = document.getElementById('search-btn');
const searchTab = document.getElementById('search-tab');
const chartsTab = document.getElementById('charts-tab');
const searchBar = document.querySelector('.search-bar');
const chartBar = document.getElementById('chart-bar');
const chartTabs = Array.from(document.querySelectorAll('.chart-tab'));
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
  if (result.type) return TYPE_LABELS[result.type] || result.type;
  return '';
}

function posterEl(result) {
  const posterUrl = result.posterUrl || (result.i && result.i.imageUrl);
  if (posterUrl) {
    const img = document.createElement('img');
    img.className = 'result-poster';
    img.src = posterUrl;
    img.alt = result.title || result.l || '';
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
    titleDiv.textContent = result.title || result.l || id;
    info.appendChild(titleDiv);

    const meta = document.createElement('div');
    meta.className = 'result-meta';

    const resultYear = result.year || result.yr || result.y;
    if (resultYear) {
      const year = document.createElement('span');
      year.className = 'result-year';
      year.textContent = resultYear;
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

function normalizeChartItem(edge, index) {
  const node = edge && edge.node;
  if (!node || !node.id) return null;
  return {
    id: node.id,
    title: node.titleText && node.titleText.text ? node.titleText.text : node.id,
    year: node.releaseYear && node.releaseYear.year ? String(node.releaseYear.year) : '',
    type: node.titleType && node.titleType.id ? node.titleType.id : '',
    posterUrl: node.primaryImage && node.primaryImage.url ? node.primaryImage.url : '',
    rank: index + 1,
  };
}

function chartCacheKey(chartKey) {
  return `playimdb.chart.${chartKey}`;
}

function readChartCache(chartKey) {
  try {
    const cached = JSON.parse(localStorage.getItem(chartCacheKey(chartKey)) || 'null');
    if (!cached || !Array.isArray(cached.items)) return null;
    if (Date.now() - cached.fetchedAtMs > CHART_CACHE_TTL_MS) return null;
    return cached.items;
  } catch (_) {
    return null;
  }
}

function writeChartCache(chartKey, items) {
  localStorage.setItem(chartCacheKey(chartKey), JSON.stringify({
    fetchedAtMs: Date.now(),
    items,
  }));
}

async function fetchChart(chartKey, forceRefresh = false) {
  const chart = CHARTS[chartKey];
  if (!chart) return;

  if (!forceRefresh) {
    const cached = readChartCache(chartKey);
    if (cached) {
      renderResults(cached);
      return;
    }
  }

  showStatus('loading');
  clearResults();

  try {
    const resp = await fetch(IMDB_GRAPHQL_URL, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        query: CHART_QUERY,
        variables: {
          first: chart.limit,
          chartType: chart.chartType,
        },
      }),
    });

    if (!resp.ok) {
      showStatus(`Could not load ${chart.label}. Try again.`, true);
      return;
    }

    const data = await resp.json();
    const edges = (((data.data || {}).chartTitles || {}).edges || []);
    const items = edges.map(normalizeChartItem).filter(Boolean);
    writeChartCache(chartKey, items);
    renderResults(items);
  } catch (_) {
    showStatus(`Could not load ${chart.label}. Try again.`, true);
  }
}

function setMode(nextMode) {
  mode = nextMode;
  const chartsMode = mode === 'charts';
  searchTab.classList.toggle('active', !chartsMode);
  chartsTab.classList.toggle('active', chartsMode);
  searchTab.setAttribute('aria-selected', chartsMode ? 'false' : 'true');
  chartsTab.setAttribute('aria-selected', chartsMode ? 'true' : 'false');
  searchBar.classList.toggle('hidden', chartsMode);
  chartBar.classList.toggle('hidden', !chartsMode);
  clearResults();
  hideStatus();

  if (chartsMode) {
    fetchChart(selectedChart);
  } else {
    searchInput.focus();
    onSearch();
  }
}

function setSelectedChart(chartKey) {
  selectedChart = chartKey;
  chartTabs.forEach((tab) => {
    tab.classList.toggle('active', tab.dataset.chart === chartKey);
  });
  fetchChart(chartKey);
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

searchTab.addEventListener('click', () => setMode('search'));
chartsTab.addEventListener('click', () => setMode('charts'));

chartTabs.forEach((tab) => {
  tab.addEventListener('click', () => setSelectedChart(tab.dataset.chart));
});

searchInput.focus();
