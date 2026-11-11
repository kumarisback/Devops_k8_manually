// Feature flags (roadmap step 22, gitops docs/FEATURE-FLAGS.md), from
// Unleash's frontend API. Where to find it, and the environment's frontend
// token, come from /config/runtime-config.json, which the cluster serves next to
// the app (not baked into the image, so one image runs in every
// environment). The token is public by design: it lets a browser ask which
// flags are on for its user in one environment, nothing more.
//
// Without that file (an environment without Unleash), the flag client never
// starts and every flag is off.

export const RUNTIME_CONFIG_PATH = '/config/runtime-config.json';

// The dashboard's task summary, released with a flag.
export const TASK_SUMMARY_FLAG = 'dashboard-task-summary';

// FlagProvider's settings while there is no flag service: never started,
// never contacted.
export const OFFLINE_FLAG_CONFIG = { url: 'http://localhost/no-flags', clientKey: 'none', appName: 'frontend' };

// The FlagProvider settings for a runtime config, or null when it names
// no flag service.
export function flagConfig(runtime) {
  const url = runtime?.unleash?.url;
  const clientKey = runtime?.unleash?.clientKey;
  if (typeof url !== 'string' || url === '' || typeof clientKey !== 'string' || clientKey === '') {
    return null;
  }
  return { url, clientKey, appName: 'frontend', refreshInterval: 30 };
}

export async function loadFlagConfig(fetchImpl = globalThis.fetch) {
  try {
    const response = await fetchImpl(RUNTIME_CONFIG_PATH, { cache: 'no-store' });
    if (!response.ok) {
      return null;
    }
    return flagConfig(await response.json());
  } catch {
    return null;
  }
}
