/**
 * Simple hash-based SPA router.
 * Routes: #/ , #/create , #/join , #/lobby/:code/:isHost , #/watch/:code/:isHost
 */

const routes = {};
let currentCleanup = null;

export function registerRoute(pattern, handler) {
  routes[pattern] = handler;
}

export function navigate(hash) {
  window.location.hash = hash;
}

export function startRouter() {
  window.addEventListener('hashchange', handleRoute);
  handleRoute();
}

function handleRoute() {
  const hash = window.location.hash || '#/';
  const app = document.getElementById('app');

  // Run cleanup of previous screen
  if (currentCleanup) {
    currentCleanup();
    currentCleanup = null;
  }

  // Match routes
  for (const [pattern, handler] of Object.entries(routes)) {
    const params = matchRoute(pattern, hash);
    if (params !== null) {
      app.innerHTML = '';
      const cleanup = handler(app, params);
      if (typeof cleanup === 'function') {
        currentCleanup = cleanup;
      }
      return;
    }
  }

  // 404 fallback
  app.innerHTML = `
    <div class="screen-container" style="text-align:center; padding-top: 100px;">
      <h1>404</h1>
      <p>Page not found</p>
      <button class="btn btn-primary" onclick="location.hash='#/'">Go Home</button>
    </div>
  `;
}

function matchRoute(pattern, hash) {
  // Convert pattern like "#/lobby/:code/:isHost" to regex
  const paramNames = [];
  const regexStr = pattern.replace(/:([^/]+)/g, (_, name) => {
    paramNames.push(name);
    return '([^/]+)';
  });
  const regex = new RegExp(`^${regexStr}$`);
  const match = hash.match(regex);
  if (!match) return null;

  const params = {};
  paramNames.forEach((name, i) => {
    params[name] = decodeURIComponent(match[i + 1]);
  });
  return params;
}
