import axios from 'axios';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://100.106.29.60:8080';

const client = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const movieApi = {
  list: (params?: { page?: number; size?: number; type?: string }) =>
    client.get('/api/movies', { params }),

  detail: (id: number) =>
    client.get(`/api/movies/${id}`),

  hot: () =>
    client.get('/api/hot'),

  latest: () =>
    client.get('/api/latest'),
};

export const dramaApi = {
  list: (params?: { page?: number; size?: number; year?: number; region?: string; genre?: string }) =>
    client.get('/api/dramas', { params }),

  detail: (id: number) =>
    client.get(`/api/dramas/${id}`),
};

export const varietyApi = {
  list: (params?: { page?: number; size?: number; year?: number; region?: string; genre?: string }) =>
    client.get('/api/varieties', { params }),

  detail: (id: number) =>
    client.get(`/api/varieties/${id}`),
};

export const animeApi = {
  list: (params?: { page?: number; size?: number; year?: number; region?: string; genre?: string }) =>
    client.get('/api/animes', { params }),

  detail: (id: number) =>
    client.get(`/api/animes/${id}`),
};

export const shortDramaApi = {
  list: (params?: { page?: number; size?: number; year?: number; region?: string; genre?: string }) =>
    client.get('/api/short-dramas', { params }),

  detail: (id: number) =>
    client.get(`/api/short-dramas/${id}`),
};

export const searchApi = {
  search: (keyword: string, params?: { page?: number; size?: number }) =>
    client.get('/api/search', { params: { keyword, ...params } }),
};

export const resourceApi = {
  online: (contentType: string, contentId: number, episodeId?: number) =>
    client.get('/api/resources/online', { params: { contentType, contentId, episodeId } }),

  magnet: (contentType: string, contentId: number, episodeId?: number) =>
    client.get('/api/resources/magnet', { params: { contentType, contentId, episodeId } }),

  cloud: (contentType: string, contentId: number, episodeId?: number) =>
    client.get('/api/resources/cloud', { params: { contentType, contentId, episodeId } }),
};

export default client;