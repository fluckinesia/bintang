(() => {
  'use strict';
  const latitude = __LAT__;
  const longitude = __LON__;
  const altitude = __ALT__;
  const accuracy = __ACCURACY__;
  const watches = new Map();
  let lastWatchId = 0;

  function position() {
    return Object.freeze({
      coords: Object.freeze({
        latitude,
        longitude,
        accuracy,
        altitude,
        altitudeAccuracy: null,
        heading: null,
        speed: null
      }),
      timestamp: Date.now()
    });
  }

  const api = Object.freeze({
    getCurrentPosition(success, error, options) {
      if (typeof success !== 'function') throw new TypeError('Success callback must be a function');
      setTimeout(() => { try { success(position()); } catch (e) { console.error(e); } }, 0);
    },
    watchPosition(success, error, options) {
      if (typeof success !== 'function') throw new TypeError('Success callback must be a function');
      const id = ++lastWatchId;
      const notify = () => { try { success(position()); } catch (e) { console.error(e); } };
      setTimeout(() => { if (watches.has(id)) notify(); }, 0);
      watches.set(id, setInterval(notify, 10000));
      return id;
    },
    clearWatch(id) {
      const timer = watches.get(id);
      if (timer !== undefined) {
        clearInterval(timer);
        watches.delete(id);
      }
    }
  });

  try {
    Object.defineProperty(navigator, 'geolocation', {
      configurable: true,
      enumerable: true,
      get: () => api
    });
  } catch (e) {
    console.warn('Geolocation injection failed', e);
  }

  try {
    const originalQuery = navigator.permissions && navigator.permissions.query
      ? navigator.permissions.query.bind(navigator.permissions)
      : null;
    if (navigator.permissions && originalQuery) {
      navigator.permissions.query = (descriptor) => {
        if (descriptor && descriptor.name === 'geolocation') {
          return Promise.resolve({
            state: 'granted',
            onchange: null,
            addEventListener() {},
            removeEventListener() {},
            dispatchEvent() { return true; }
          });
        }
        return originalQuery(descriptor);
      };
    }
  } catch (e) {
    console.warn('Permissions query override failed', e);
  }

  try {
    window.__FIXED_GPS__ = { latitude, longitude, altitude, accuracy, mode: 'locked' };
  } catch (e) {
    console.warn('Unable to expose fixed GPS object', e);
  }
})();
