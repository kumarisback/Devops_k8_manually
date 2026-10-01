# ---- Build stage ----
# Node 24 LTS (Node 20 reached end of life in April 2026).
FROM node:24-alpine AS build
WORKDIR /app

ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# Install exactly what package-lock.json pins.
COPY package.json package-lock.json ./
RUN npm ci

# Copy sources and build static assets
COPY . .
RUN npm run build

# ---- Run stage ----
FROM nginx:alpine
# Copy built static files to Nginx public directory
COPY --from=build /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration for routing support
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
