/* eslint-disable @typescript-eslint/no-require-imports */
const fs = require('fs');
const path = require('path');
const https = require('https');

const modelsDir = path.join(__dirname, 'public', 'models');

if (!fs.existsSync(modelsDir)) {
  fs.mkdirSync(modelsDir, { recursive: true });
}

const files = [
  'tiny_face_detector_model-weights_manifest.json',
  'tiny_face_detector_model-shard1',
  'face_landmark_68_tiny_model-weights_manifest.json',
  'face_landmark_68_tiny_model-shard1',
  'face-api.min.js'
];

const sources = [
  'https://cdn.jsdelivr.net/npm/face-api.js@0.22.2/weights/',
  'https://unpkg.com/face-api.js@0.22.2/weights/',
  'https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/weights/'
];

function downloadFile(filename, sourceIndex = 0) {
  if (sourceIndex >= sources.length) {
    console.error(`❌ Failed to download ${filename} from all sources.`);
    process.exit(1);
  }

  let url;
  if (filename === 'face-api.min.js') {
    const baseUrls = [
      'https://cdn.jsdelivr.net/npm/face-api.js@0.22.2/dist/',
      'https://unpkg.com/face-api.js@0.22.2/dist/',
      'https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/dist/'
    ];
    url = `${baseUrls[sourceIndex]}${filename}`;
  } else {
    url = `${sources[sourceIndex]}${filename}`;
  }
  const dest = path.join(modelsDir, filename);

  console.log(`Downloading ${filename} from source ${sourceIndex}...`);

  https.get(url, (res) => {
    if (res.statusCode !== 200) {
      console.warn(`⚠️ Source ${sourceIndex} returned code ${res.statusCode} for ${filename}. Trying next source...`);
      downloadFile(filename, sourceIndex + 1);
      return;
    }

    const fileStream = fs.createWriteStream(dest);
    res.pipe(fileStream);

    fileStream.on('finish', () => {
      fileStream.close();
      console.log(`✅ Downloaded ${filename}`);
      // Proceed to download next file
      downloadNext();
    });
  }).on('error', (err) => {
    console.warn(`⚠️ Source ${sourceIndex} error: ${err.message}. Trying next source...`);
    downloadFile(filename, sourceIndex + 1);
  });
}

let currentIndex = 0;
function downloadNext() {
  if (currentIndex < files.length) {
    const file = files[currentIndex];
    currentIndex++;
    downloadFile(file);
  } else {
    console.log('🎉 All face-api models downloaded successfully to public/models!');
  }
}

downloadNext();
