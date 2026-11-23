"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { FieldEditor } from "@/components/editor/field-editor";
import { RequireLogin } from "@/components/require-login";
import { api } from "@/lib/api";
import type { RequestDetail } from "@/lib/types";

export default function EditRequestPage() {
  return <RequireLogin wide>{() => <Loader />}</RequireLogin>;
}

function Loader() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [detail, setDetail] = useState<RequestDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<RequestDetail>(`/api/v1/requests/${id}`)
      .then((d) => {
        // 발송한 요청은 편집할 수 없으니 상세 화면으로
        if (d.status !== "DRAFT") router.replace(`/requests/${id}`);
        else setDetail(d);
      })
      .catch((e: Error) => setError(e.message));
  }, [id, router]);

  if (error) return <p className="err">{error}</p>;
  if (!detail) return <p className="muted">불러오는 중…</p>;
  return <FieldEditor initial={detail} />;
}
