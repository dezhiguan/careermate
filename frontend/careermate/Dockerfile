# Multi-stage build for CareerMate frontend on k3s (Server 3).
# Build context: careermate repository root.
#
#   docker build -f frontend/careermate/Dockerfile \
#     --build-arg VITE_API_BASE_URL=/api \
#     --build-arg VITE_BASE_PATH=/ \
#     -t careermate/frontend:latest .

FROM node:20-alpine AS build
WORKDIR /src
ARG VITE_API_BASE_URL=/api
ARG VITE_BASE_PATH=/
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_BASE_PATH=${VITE_BASE_PATH}
COPY frontend/careermate/package.json frontend/careermate/package-lock.json ./
RUN npm ci
COPY frontend/careermate/ ./
RUN npm run build

# Pre-built dist from CI (SKIP_FRONTEND_BUILD=1): docker build --target runtime-prebuilt
FROM nginx:1.27-alpine AS runtime-prebuilt
COPY deploy/k8s/careermate/frontend-nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/careermate/dist /usr/share/nginx/html
EXPOSE 80

FROM nginx:1.27-alpine AS runtime
COPY deploy/k8s/careermate/frontend-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
