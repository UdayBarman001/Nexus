const fs = require('fs');
const path = require('path');

// Ensure all child processes and worker threads spawned by Next.js also preload this patch
// Use forward slashes to avoid backslash unescaping in NODE_OPTIONS on Windows
const selfPath = path.resolve(__dirname, 'patch-fs.cjs').replace(/\\/g, '/');
if (!process.env.NODE_OPTIONS || !process.env.NODE_OPTIONS.includes('patch-fs.cjs')) {
  process.env.NODE_OPTIONS = `${process.env.NODE_OPTIONS || ''} -r "${selfPath}"`.trim();
}

function patchError(err) {
  if (err && (err.code === 'EISDIR' || err.code === 'UNKNOWN' || err.errno === -4068)) {
    const newErr = new Error(`EINVAL: invalid argument, readlink '${err.path || ''}'`);
    newErr.code = 'EINVAL';
    newErr.errno = -4071;
    newErr.syscall = 'readlink';
    newErr.path = err.path;
    return newErr;
  }
  return err;
}

const origReadlink = fs.readlink;
fs.readlink = function (pathArg, ...args) {
  const cb = args[args.length - 1];
  if (typeof cb === 'function') {
    args[args.length - 1] = function (err, linkString) {
      cb(patchError(err), linkString);
    };
  }
  return origReadlink.call(fs, pathArg, ...args);
};

const origReadlinkSync = fs.readlinkSync;
fs.readlinkSync = function (pathArg, ...args) {
  try {
    return origReadlinkSync.call(fs, pathArg, ...args);
  } catch (err) {
    throw patchError(err);
  }
};

if (fs.promises) {
  const origPromisesReadlink = fs.promises.readlink;
  fs.promises.readlink = async function (pathArg, ...args) {
    try {
      return await origPromisesReadlink.call(fs.promises, pathArg, ...args);
    } catch (err) {
      throw patchError(err);
    }
  };
}
