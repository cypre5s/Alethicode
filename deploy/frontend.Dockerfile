FROM node:20.19-bullseye AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci --legacy-peer-deps
COPY frontend ./
RUN npm run build

FROM nginx:1.29-alpine
RUN mkdir -p /var/cache/nginx/api && chown -R nginx:nginx /var/cache/nginx
COPY deploy/frontend-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
