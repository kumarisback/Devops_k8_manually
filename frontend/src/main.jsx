import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { FlagProvider } from '@unleash/proxy-client-react'
import './index.css'
import App from './App.jsx'
import { startTracing } from './lib/otel.js'
import { loadFlagConfig, OFFLINE_FLAG_CONFIG } from './lib/flags.js'

// Before the first render, so the first API calls are traced too.
startTracing()

// Feature flags (src/lib/flags.js): from Unleash where the environment has
// it; otherwise the client never starts and every flag is off.
loadFlagConfig().then((flags) => {
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <FlagProvider config={flags ?? OFFLINE_FLAG_CONFIG} startClient={flags !== null}>
        <App />
      </FlagProvider>
    </StrictMode>,
  )
})
