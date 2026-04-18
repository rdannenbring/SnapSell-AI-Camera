import { spawn } from 'node:child_process';

let qrcode = null;
try {
  const mod = await import('qrcode-terminal');
  qrcode = mod.default || mod;
} catch {
  console.warn(
    '[tunnel:qr:lt] Optional dependency "qrcode-terminal" is not installed yet.\n' +
      'Run: npm install\n' +
      'Continuing without QR output (URL will still be printed).\n'
  );
}

const port = process.env.TUNNEL_PORT || '3000';

console.log(`Starting LocalTunnel for http://localhost:${port}...`);
console.log('A QR code will be printed once the public HTTPS URL is detected.\n');

const child = spawn('npx', ['localtunnel', '--port', port], {
  stdio: ['inherit', 'pipe', 'pipe'],
  shell: false,
});

let qrShown = false;
const urlRegex = /https:\/\/[-a-zA-Z0-9@:%._+~#=]{2,256}\.[a-z]{2,}\b[-a-zA-Z0-9@:%_+.~#?&/=]*/g;
const localtunnelRegex = /https:\/\/[a-z0-9-]+\.loca\.lt\b/;

function handleOutput(chunk, writeFn) {
  const text = chunk.toString();
  writeFn(text);

  if (qrShown) return;

  const matches = text.match(urlRegex);
  if (!matches) return;

  const tunnelUrl = matches.find((url) => localtunnelRegex.test(url));
  if (!tunnelUrl) return;

  qrShown = true;
  console.log('\nPublic URL:');
  console.log(tunnelUrl);
  if (qrcode?.generate) {
    console.log('\nScan this QR code on your Android phone:');
    qrcode.generate(tunnelUrl, { small: true });
    console.log('');
  }
}

child.stdout.on('data', (chunk) => handleOutput(chunk, (text) => process.stdout.write(text)));
child.stderr.on('data', (chunk) => handleOutput(chunk, (text) => process.stderr.write(text)));

child.on('error', (err) => {
  console.error('Failed to start localtunnel:', err.message);
  process.exit(1);
});

child.on('close', (code) => {
  if (code !== 0) {
    console.error(`localtunnel exited with code ${code}`);
  }
  process.exit(code ?? 1);
});
