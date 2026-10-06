const http = require('http');
const { spawn } = require('child_process');

async function getAuthToken() {
  const res = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'user@example.com', password: 'Password123' })
  });
  if (!res.ok) throw new Error(`Auth failed: ${res.status}`);
  return await res.json();
}

async function main() {
  console.log('1. Getting auth token for user@example.com...');
  const authData = await getAuthToken();
  console.log('Logged in successfully. User:', authData.user);

  const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  const debuggingPort = 9222;

  console.log('2. Starting Headless Chrome on port', debuggingPort);
  const chromeProcess = spawn(chromePath, [
    '--headless=new',
    `--remote-debugging-port=${debuggingPort}`,
    '--no-sandbox',
    '--disable-gpu',
    '--disable-dev-shm-usage',
    '--window-size=1280,800',
    'about:blank'
  ]);

  // Wait for remote debugging to be ready
  let wsUrl = null;
  for (let i = 0; i < 20; i++) {
    await new Promise(r => setTimeout(r, 500));
    try {
      const versionRes = await fetch(`http://localhost:${debuggingPort}/json/version`);
      if (versionRes.ok) {
        const json = await versionRes.json();
        wsUrl = json.webSocketDebuggerUrl;
        break;
      }
    } catch (e) {}
  }

  if (!wsUrl) {
    chromeProcess.kill();
    throw new Error('Could not connect to Chrome DevTools port');
  }

  console.log('3. Connected to Chrome CDP at', wsUrl);

  const ws = new WebSocket(wsUrl);
  let id = 1;
  const callbacks = new Map();

  function send(method, params = {}) {
    return new Promise((resolve, reject) => {
      const msgId = id++;
      callbacks.set(msgId, { resolve, reject });
      ws.send(JSON.stringify({ id: msgId, method, params }));
    });
  }

  const consoleLogs = [];
  const errors = [];

  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && callbacks.has(msg.id)) {
      const cb = callbacks.get(msg.id);
      callbacks.delete(msg.id);
      if (msg.error) cb.reject(msg.error);
      else cb.resolve(msg.result);
    } else if (msg.method === 'Runtime.consoleAPICalled') {
      const args = msg.params.args.map(a => a.value || a.description || JSON.stringify(a)).join(' ');
      consoleLogs.push(`[${msg.params.type}] ${args}`);
      if (msg.params.type === 'error') {
        errors.push(args);
      }
    } else if (msg.method === 'Runtime.exceptionThrown') {
      const text = msg.params.exceptionDetails?.exception?.description || msg.params.exceptionDetails?.text;
      errors.push(`Exception: ${text}`);
    }
  };

  await new Promise(r => ws.onopen = r);

  // Create new target/page
  const targetRes = await send('Target.createTarget', { url: 'http://localhost:3000/login' });
  const targetId = targetRes.targetId;

  // Attach to target
  const attachRes = await send('Target.attachToTarget', { targetId, flatten: true });
  const sessionId = attachRes.sessionId;

  function sendSession(method, params = {}) {
    return new Promise((resolve, reject) => {
      const msgId = id++;
      callbacks.set(msgId, { resolve, reject });
      ws.send(JSON.stringify({ id: msgId, sessionId, method, params }));
    });
  }

  await sendSession('Runtime.enable');
  await sendSession('Page.enable');

  // Set localStorage credentials
  console.log('4. Setting localStorage credentials on http://localhost:3000...');
  await new Promise(r => setTimeout(r, 2000));
  await sendSession('Runtime.evaluate', {
    expression: `
      localStorage.setItem('nexus_token', ${JSON.stringify(authData.token)});
      localStorage.setItem('nexus_user', ${JSON.stringify(JSON.stringify(authData.user))});
    `
  });

  // Navigate to /documents
  console.log('5. Navigating to http://localhost:3000/documents...');
  await sendSession('Page.navigate', { url: 'http://localhost:3000/documents' });

  // Wait for network idle and rendering
  console.log('6. Waiting for /documents page render and API responses...');
  await new Promise(r => setTimeout(r, 5000));

  // Evaluate page content
  const pageEvaluation = await sendSession('Runtime.evaluate', {
    expression: `
      (() => {
        const title = document.title;
        const bodyText = document.body.innerText;
        // Look for header stats or stat cards
        const statsCards = Array.from(document.querySelectorAll('*')).filter(el => 
          el.innerText && (
            el.innerText.includes('Total Documents') ||
            el.innerText.includes('Vectorized') ||
            el.innerText.includes('Total Chunks')
          )
        ).map(el => el.innerText.slice(0, 100));

        // Check if ReferenceError exists anywhere in document
        const hasReferenceError = bodyText.includes('ReferenceError') || bodyText.includes('before initialization');

        return {
          title,
          url: window.location.href,
          hasReferenceError,
          statsFound: statsCards.length > 0,
          sampleBody: bodyText.slice(0, 500)
        };
      })()
    `,
    returnByValue: true
  });

  console.log('\n--- RESULTS ---');
  console.log('Page Evaluation:', JSON.stringify(pageEvaluation.result.value, null, 2));
  console.log('Console Errors count:', errors.length);
  if (errors.length > 0) {
    console.log('Console Errors:', errors);
  } else {
    console.log('No console errors detected! ALL CLEAR.');
  }

  // Cleanup
  chromeProcess.kill();
  process.exit(0);
}

main().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
