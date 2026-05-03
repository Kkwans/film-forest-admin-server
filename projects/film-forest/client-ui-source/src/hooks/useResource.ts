import { useState, useEffect } from 'react';
import { resourceApi } from '@/lib/api';

export interface OnlineResource {
  id: number;
  sourceName: string;
  sourceUrl: string;
  sort?: number;
}

export interface MagnetResource {
  id: number;
  title: string;
  magnetUrl: string;
  resolution?: string;
  hasSubtitle?: boolean;
  isSpecialSub?: boolean;
  sort?: number;
}

export interface CloudResource {
  id: number;
  title: string;
  shareUrl: string;
  password?: string;
  storageName?: string;
}

export function useResource(contentType: string, contentId: number, episodeId?: number) {
  const [onlineResources, setOnlineResources] = useState<OnlineResource[]>([]);
  const [magnetResources, setMagnetResources] = useState<MagnetResource[]>([]);
  const [cloudResources, setCloudResources] = useState<CloudResource[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchResources = async () => {
    if (!contentId) return;
    setLoading(true);
    try {
      const [onlineRes, magnetRes, cloudRes] = await Promise.allSettled([
        resourceApi.online(contentType, contentId, episodeId),
        resourceApi.magnet(contentType, contentId, episodeId),
        resourceApi.cloud(contentType, contentId, episodeId),
      ]);

      if (onlineRes.status === 'fulfilled') {
        const data = (onlineRes.value as any)?.data?.data || (onlineRes.value as any)?.data || [];
        setOnlineResources(Array.isArray(data) ? data : []);
      }
      if (magnetRes.status === 'fulfilled') {
        const data = (magnetRes.value as any)?.data?.data || (magnetRes.value as any)?.data || [];
        setMagnetResources(Array.isArray(data) ? data : []);
      }
      if (cloudRes.status === 'fulfilled') {
        const data = (cloudRes.value as any)?.data?.data || (cloudRes.value as any)?.data || [];
        setCloudResources(Array.isArray(data) ? data : []);
      }
    } catch (e) {
      console.error('[useResource] fetch failed', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchResources();
  }, [contentType, contentId, episodeId]);

  return {
    onlineResources,
    magnetResources,
    cloudResources,
    loading,
    refetch: fetchResources,
  };
}