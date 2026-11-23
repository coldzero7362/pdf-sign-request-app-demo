"use client";

import { useEffect, useState } from "react";
import { fetchMe, type Me } from "@/lib/api";

/** 현재 사용자. undefined = 확인 중, null = 로그인 안 함. */
export function useMe() {
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchMe()
      .then(setMe)
      .catch((e: Error) => setError(e.message));
  }, []);

  return { me, error };
}
