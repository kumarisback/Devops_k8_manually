# DevOps Kubernetes Repository

This repository contains the DevOps assets for a containerized application deployment setup on Kubernetes.

## What is included

- Kubernetes manifests for the frontend, user service, order service, and MySQL
- Ingress and service definitions for traffic routing
- Dockerfiles for the frontend and backend services
- Jenkins pipeline configuration for CI/CD
- Sample folder structure for organizing manifests in a production-style way

## Main folders

- frontend/ — frontend application source, Dockerfile, NGINX config, and Jenkins pipeline
- order-service/ — backend service source and build files
- user-service/ — backend service source and build files
- Actual k8 file/ — Kubernetes YAML manifests for deployment, services, ingress, secrets, and databases
- material/ — supporting notes or examples

## Typical workflow

1. Build the application images using Docker.
2. Push the images to your container registry.
3. Apply the Kubernetes manifests in the appropriate order.
4. Validate pods, services, and ingress routes.

## Notes

This repo is intended to be used as a deployment and operations base for Kubernetes-based microservices.

