"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { RequireLogin } from "@/components/require-login";
import { api } from "@/lib/api";
import type { DocumentView, RequestDetail } from "@/lib/types";

const MAX_BYTES = 20 * 1024 * 1024;

export default function NewRequestPage() {
  return <RequireLogin>{() => <NewRequestForm />}</RequireLogin>;
}

function NewRequestForm() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function onFile(f: File | null) {
    setError(null);
    if (f && f.size > MAX_BYTES) {
      setError("파일은 20MB 이하만 올릴 수 있습니다.");
      return;
    }
    setFile(f);
    if (f && !title) setTitle(f.name.replace(/\.pdf$/i, ""));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.append("file", file);
      const doc = await api<DocumentView>("/api/v1/documents", { method: "POST", body: form });
      const req = await api<RequestDetail>("/api/v1/requests", {
        method: "POST",
        json: { documentId: doc.id, title: title.trim() || doc.fileName },
      });
      router.push(`/requests/${req.id}/edit`);
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  return (
    <>
      <h1>새 요청</h1>
      <p className="muted">서명이나 입력을 받을 PDF를 올리세요. 20MB, 50쪽 이하이고 암호가 없어야 합니다.</p>
      <form className="card form" onSubmit={onSubmit}>
        <label>
          PDF 파일
          <input type="file" accept="application/pdf,.pdf" onChange={(e) => onFile(e.target.files?.[0] ?? null)} required />
        </label>
        <label>
          요청 제목
          <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} placeholder="예: 근로계약서 서명" />
        </label>
        {error && <p className="err">{error}</p>}
        <button type="submit" className="btn btn-accent" disabled={!file || busy}>
          {busy ? "올리는 중…" : "올리고 칸 배치하기"}
        </button>
      </form>
    </>
  );
}
