"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { RequireLogin } from "@/components/require-login";
import { api } from "@/lib/api";
import type { TemplateView } from "@/lib/types";

const MAX_BYTES = 20 * 1024 * 1024;

export default function NewTemplatePage() {
  return <RequireLogin>{() => <NewTemplateForm />}</RequireLogin>;
}

function NewTemplateForm() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function onFile(f: File | null) {
    setError(null);
    if (f && f.size > MAX_BYTES) {
      setError("파일은 20MB 이하만 올릴 수 있습니다.");
      return;
    }
    setFile(f);
    if (f && !name) setName(f.name.replace(/\.pdf$/i, ""));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.append("file", file);
      if (name.trim()) form.append("name", name.trim());
      const t = await api<TemplateView>("/api/v1/templates", { method: "POST", body: form });
      router.push(`/templates/${t.templateNo}`);
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  return (
    <>
      <h1>새 템플릿</h1>
      <p className="muted">서명받을 PDF를 올리세요. 20MB, 50쪽 이하이고 암호가 없어야 합니다.</p>
      <form className="card form" onSubmit={onSubmit}>
        <label>
          PDF 파일
          <input type="file" accept="application/pdf,.pdf" onChange={(e) => onFile(e.target.files?.[0] ?? null)} required />
        </label>
        <label>
          템플릿 이름
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} placeholder="예: 근로계약서" />
        </label>
        {error && <p className="err">{error}</p>}
        <button type="submit" className="btn btn-accent" disabled={!file || busy}>
          {busy ? "올리는 중…" : "올리고 편집하기"}
        </button>
      </form>
    </>
  );
}
