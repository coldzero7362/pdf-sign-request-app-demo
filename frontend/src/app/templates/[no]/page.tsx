"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { RequireLogin } from "@/components/require-login";
import { TemplateEditor } from "@/components/template/template-editor";
import { api } from "@/lib/api";
import type { TemplateView } from "@/lib/types";

export default function TemplateEditPage() {
  return <RequireLogin wide>{() => <Loader />}</RequireLogin>;
}

function Loader() {
  const { no } = useParams<{ no: string }>();
  const [t, setT] = useState<TemplateView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<TemplateView>(`/api/v1/templates/${encodeURIComponent(no)}`)
      .then(setT)
      .catch((e: Error) => setError(e.message));
  }, [no]);

  if (error) return <p className="err">{error}</p>;
  if (!t) return <p className="muted">불러오는 중…</p>;
  return <TemplateEditor initial={t} />;
}
