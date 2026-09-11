FROM node:22-alpine AS build
WORKDIR /build

# package.json and the lockfile first, for the same layer-caching reason as the
# backend. `npm ci` rather than `npm install`: it installs exactly what the
# lockfile pins and fails if the two disagree, which is what a build should do.
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -q --spider http://localhost/ || exit 1
