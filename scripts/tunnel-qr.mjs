import { spawn } from 'node:child_process';

let qrcode = null;
try {
  const mod = await import('qrcode-terminal');
  qrcode = mod.default || mod;
} catch {
  console.warn(
    '[tunnel:qr] Optional dependency "qrcode-terminal" is not installed yet.\n' +
      'Run: npm install\n' +
      'Continuing without QR output (URL will still be printed).\n'
  );
}

const tunnelTarget = process.env.TUNNEL_TARGET || 'http://localhost:3000';

console.log(`Starting Cloudflare tunnel for ${tunnelTarget}...`);
console.log('A QR code will be printed once the public HTTPS URL is detected.\n');

const child = spawn('npx', ['cloudflared', 'tunnel', '--url', tunnelTarget], {
  stdio: ['inherit', 'pipe', 'pipe'],
  shell: false,
});

let qrShown = false;
const urlRegex = /https:\/\/[-a-zA-Z0-9@:%._+~#=]{2,256}\.[a-z]{2,}\b[-a-zA-Z0-9@:%_+.~#?&/=]*/g;
const tryCloudflareRegex = /https:\/\/[a-z0-9-]+\.trycloudflare\.com\b/;
const seenCandidates = new Set();

function handleOutput(chunk, writeFn) {
  const text = chunk.toString();
  writeFn(text);

  if (qrShown) return;

  const matches = text.match(urlRegex);
  if (!matches) return;

  const candidates = matches.filter((url) => tryCloudflareRegex.test(url));
  if (candidates.length === 0) return;

  for (const tunnelUrl of candidates) {
    if (seenCandidates.has(tunnelUrl)) continue;
    seenCandidates.add(tunnelUrl);

    if (qrShown) continue;
    qrShown = true;
    console.log('\nPublic URL:');
    console.log(tunnelUrl);
    console.log('\nTip: if the link fails at first, wait 10–20 seconds and refresh (quick tunnel DNS propagation).');
    if (qrcode?.generate) {
      console.log('\nScan this QR code on your Android phone:');
      qrcode.generate(tunnelUrl, { small: true });
      console.log('');
    }
  }
}

child.stdout.on('data', (chunk) => handleOutput(chunk, (text) => process.stdout.write(text)));
child.stderr.on('data', (chunk) => handleOutput(chunk, (text) => process.stderr.write(text)));

child.on('error', (err) => {
  console.error('Failed to start cloudflared tunnel:', err.message);
  process.exit(1);
});

child.on('close', (code) => {
  if (code !== 0) {
    console.error(`cloudflared exited with code ${code}`);
  }
  process.exit(code ?? 1);
});
