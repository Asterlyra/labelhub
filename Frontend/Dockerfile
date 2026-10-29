FROM node:20-alpine AS build

WORKDIR /app

COPY Frontend/package.json Frontend/package-lock.json ./
RUN npm ci

COPY Frontend/ ./

ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

FROM nginx:1.27-alpine

ARG BACKEND_UPSTREAM=backend
ARG BACKEND_INTERNAL_PORT=8080
ENV BACKEND_UPSTREAM=${BACKEND_UPSTREAM}
ENV BACKEND_INTERNAL_PORT=${BACKEND_INTERNAL_PORT}

COPY deploy/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
