import './style.css';
import { registerRoute, startRouter } from './router.js';
import { renderHome } from './screens/home.js';
import { renderCreate } from './screens/create.js';
import { renderJoin } from './screens/join.js';
import { renderLobby } from './screens/lobby.js';
import { renderWatch } from './screens/watch.js';

// Register routes
registerRoute('#/', (container) => renderHome(container));
registerRoute('#/create', (container) => renderCreate(container));
registerRoute('#/join', (container) => renderJoin(container));
registerRoute('#/lobby/:code/:isHost', (container, params) => renderLobby(container, params));
registerRoute('#/watch/:code/:isHost', (container, params) => renderWatch(container, params));

// Global error handler to prevent silent failures
window.addEventListener('error', (e) => {
  console.error('[MovieSync] Uncaught error:', e.error);
});

window.addEventListener('unhandledrejection', (e) => {
  console.error('[MovieSync] Unhandled promise rejection:', e.reason);
});

// Start the router
startRouter();
