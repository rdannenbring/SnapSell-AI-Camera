import { Capacitor } from '@capacitor/core';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { FilePicker } from '@capawesome/capacitor-file-picker';
import type { PhotoData } from '../types';

export function isNativeAndroid() {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
}

export function getDefaultSaveLocation() {
  if (isNativeAndroid()) {
    return '/storage/emulated/0/Pictures/SnapSell';
  }
  return 'Downloads';
}

export async function pickSaveDirectory() {
  if (!isNativeAndroid()) {
    throw new Error('Directory picker is only available in the native Android app.');
  }

  const result = await FilePicker.pickDirectory();
  const selectedPath = (result as { path?: string; uri?: string }).path || (result as { uri?: string }).uri;
  if (!selectedPath) {
    throw new Error('Directory picker returned no path.');
  }
  return selectedPath;
}

async function ensureStoragePermissions() {
  // File picker permission can be unsupported/unnecessary for directory-tree SAF flow on newer Android.
  try {
    await FilePicker.requestPermissions();
  } catch {
    // Ignore and continue with SAF-based operations.
  }

  // Filesystem public storage permission where supported
  try {
    await Filesystem.requestPermissions();
  } catch {
    // On newer Android versions this may be a no-op/deprecated path; ignore.
  }
}

function sanitizeFileNamePart(value: string) {
  return value.replace(/[^a-zA-Z0-9-_ ]/g, '').trim().replace(/\s+/g, '-');
}

function buildFileName(baseName: string, index: number, timestamp: number) {
  const date = new Date(timestamp);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  const ss = String(date.getSeconds()).padStart(2, '0');
  return `${baseName}-${index + 1}-${yyyy}${mm}${dd}-${hh}${min}${ss}.jpg`;
}

function isContentUri(value: string) {
  return value.startsWith('content://');
}

function tryBuildAbsolutePathFromTreeUri(saveLocation: string) {
  if (!isContentUri(saveLocation) || !saveLocation.includes('/tree/')) {
    return null;
  }

  try {
    const treeDocId = saveLocation.split('/tree/')[1];
    if (!treeDocId) {
      return null;
    }
    const decoded = decodeURIComponent(treeDocId);
    if (!decoded.startsWith('primary:')) {
      return null;
    }
    return `/storage/emulated/0/${decoded.slice('primary:'.length)}`;
  } catch {
    return null;
  }
}

function buildDestinationCandidates(saveLocation: string, fileName: string) {
  const normalizedSaveLocation = saveLocation.endsWith('/') ? saveLocation.slice(0, -1) : saveLocation;
  const candidates = new Set<string>();

  // Basic path candidate.
  candidates.add(`${normalizedSaveLocation}/${fileName}`);

  // If picker returned a tree content URI, add likely document URI variants.
  if (isContentUri(normalizedSaveLocation) && normalizedSaveLocation.includes('/tree/')) {
    try {
      const [prefix, rawTreeDocId] = normalizedSaveLocation.split('/tree/');
      if (prefix && rawTreeDocId) {
        const decodedTreeDocId = decodeURIComponent(rawTreeDocId);
        const childDocId = `${decodedTreeDocId}/${fileName}`;
        const encodedChildDocId = encodeURIComponent(childDocId);

        candidates.add(`${prefix}/document/${encodedChildDocId}`);
        candidates.add(`${prefix}/tree/${rawTreeDocId}/document/${encodedChildDocId}`);
      }
    } catch {
      // Ignore URI parsing issues and keep basic candidate.
    }
  }

  // If tree URI maps to primary storage, also try an absolute filesystem path.
  const inferredAbsolutePath = tryBuildAbsolutePathFromTreeUri(normalizedSaveLocation);
  if (inferredAbsolutePath) {
    candidates.add(`${inferredAbsolutePath}/${fileName}`);
  }

  return Array.from(candidates);
}

/**
 * Compress and resize a data URL image to reduce memory footprint.
 * Converts to JPEG at the specified quality, max 4096px on longest side.
 */
async function compressImage(dataUrl: string, maxDimension = 4096, quality = 0.85): Promise<string> {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      let { width, height } = img;
      // Skip compression if image is already small enough
      if (width <= maxDimension && height <= maxDimension && dataUrl.length < 2_000_000) {
        resolve(dataUrl);
        return;
      }
      // Scale down if needed
      if (width > maxDimension || height > maxDimension) {
        const ratio = Math.min(maxDimension / width, maxDimension / height);
        width = Math.round(width * ratio);
        height = Math.round(height * ratio);
      }
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d');
      if (!ctx) { resolve(dataUrl); return; }
      ctx.drawImage(img, 0, 0, width, height);
      resolve(canvas.toDataURL('image/jpeg', quality));
    };
    img.onerror = () => resolve(dataUrl);
    img.src = dataUrl;
  });
}

async function savePhotoWithFilesystem(photo: PhotoData, fileName: string, fallbackFolderName: string, quality: number) {
  const compressedUrl = await compressImage(photo.url, 4096, quality);
  const base64Data = compressedUrl.includes(',') ? compressedUrl.split(',')[1] : compressedUrl;

  await Filesystem.writeFile({
    path: `${fallbackFolderName}/${fileName}`,
    data: base64Data,
    directory: Directory.Documents,
    recursive: true,
  });

  const uri = await Filesystem.getUri({
    path: `${fallbackFolderName}/${fileName}`,
    directory: Directory.Documents,
  });

  return uri.uri;
}

async function savePhotoToSelectedLocation(photo: PhotoData, fileName: string, saveLocation: string, quality: number) {
  const compressedUrl = await compressImage(photo.url, 4096, quality);
  const base64Data = compressedUrl.includes(',') ? compressedUrl.split(',')[1] : compressedUrl;
  const destinationCandidates = buildDestinationCandidates(saveLocation, fileName);
  const errors: string[] = [];

  // 1) Try direct write for non-content destinations first.
  for (const destination of destinationCandidates) {
    if (isContentUri(destination)) {
      continue;
    }
    try {
      await Filesystem.writeFile({
        path: destination,
        data: base64Data,
        recursive: true,
      });
      return destination;
    } catch (err) {
      errors.push(`direct write failed (${destination}): ${String(err)}`);
    }
  }

  // 2) Try copy via temp file to each destination candidate.
  const tempPath = `SnapSell/tmp/${fileName}`;
  try {
    await Filesystem.writeFile({
      path: tempPath,
      data: base64Data,
      directory: Directory.Cache,
      recursive: true,
    });

    const tempUri = await Filesystem.getUri({
      path: tempPath,
      directory: Directory.Cache,
    });

    let copiedTo: string | null = null;
    for (const destination of destinationCandidates) {
      try {
        await FilePicker.copyFile({
          from: tempUri.uri,
          to: destination,
          overwrite: true,
        });
        copiedTo = destination;
        break;
      } catch (err) {
        errors.push(`copy failed (${destination}): ${String(err)}`);
      }
    }

    if (!copiedTo) {
      throw new Error('No destination candidate accepted copy.');
    }

    await Filesystem.deleteFile({
      path: tempPath,
      directory: Directory.Cache,
    });

    return copiedTo;
  } catch (err) {
    errors.push(`copy via SAF failed: ${String(err)}`);
    try {
      await Filesystem.deleteFile({
        path: tempPath,
        directory: Directory.Cache,
      });
    } catch {
      // Ignore cleanup errors.
    }
    throw new Error(`Failed to write to selected save location. ${errors.join(' | ')}`);
  }
}

export async function savePhotosToDevice(
  photos: PhotoData[],
  saveLocation: string,
  itemName?: string,
  imageQuality?: number,
) {
  const savedPaths: string[] = [];
  let usedFallback = false;
  const safeBaseName = sanitizeFileNamePart(itemName || 'Item') || 'Item';

  if (!isNativeAndroid()) {
    for (let i = 0; i < photos.length; i++) {
      const photo = photos[i];
      const fileName = buildFileName(safeBaseName, i, photo.timestamp || Date.now());
      const anchor = document.createElement('a');
      anchor.href = photo.url;
      anchor.download = fileName;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      savedPaths.push(fileName);
    }
    return savedPaths;
  }

  await ensureStoragePermissions();

  for (let i = 0; i < photos.length; i++) {
    const photo = photos[i];
    const fileName = buildFileName(safeBaseName, i, photo.timestamp || Date.now());

    if (isNativeAndroid() && saveLocation) {
      try {
        const destination = await savePhotoToSelectedLocation(photo, fileName, saveLocation, imageQuality ?? 0.85);
        savedPaths.push(destination);
        continue;
      } catch (err) {
        console.warn('Selected save location write failed; using fallback', {
          saveLocation,
          fileName,
          error: String(err),
        });
        // Fall through to app documents save if direct copy to selected path fails.
      }
    }

    const fallbackUri = await savePhotoWithFilesystem(photo, fileName, 'SnapSell', imageQuality ?? 0.85);
    usedFallback = true;
    savedPaths.push(fallbackUri);
  }

  return {
    savedPaths,
    usedFallback,
  };
}
