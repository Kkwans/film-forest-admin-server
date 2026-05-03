FROM node:18-alpine

WORKDIR /app

# 复制 package.json 和依赖
COPY package.json ./

# 生产环境只装 dependencies（不装 devDependencies）
RUN npm ci --omit=dev

# 复制构建产物
COPY .next ./
COPY next.config.ts ./

EXPOSE 3000

CMD ["next", "start", "-p", "3000"]
