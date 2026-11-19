import { useState, useEffect } from "react";
import { X } from "lucide-react";

const ESTADO_INICIAL = {
  titulo: "",
  descricao: "",
  data: "",
  horaInicio: "18:00",
  horaFim: "23:00",
  intervaloMinutos: 30,
  local: "Endereço da empresa (padrão)",
  valorHora: "",
  quantidadeVagas: 1,
  ofereceRefeicao: false,
  vagaRelampago: false,
};

export function VagaFormModal({
  aberto,
  vagaEdicao,
  somenteLeitura = false,
  onClose,
  onSalvar,
  onSolicitarExclusao,
}) {
  const [form, setForm] = useState(ESTADO_INICIAL);
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (vagaEdicao) {
      setForm({
        titulo: vagaEdicao.titulo || "",
        descricao: vagaEdicao.descricao || "",
        data: vagaEdicao.data || "",
        horaInicio: vagaEdicao.horaInicio || "18:00",
        horaFim: vagaEdicao.horaFim || "23:00",
        intervaloMinutos: vagaEdicao.intervaloMinutos ?? 30,
        local: vagaEdicao.local || "Endereço da empresa (padrão)",
        valorHora: vagaEdicao.valorHora ?? "",
        quantidadeVagas: vagaEdicao.quantidadeVagas ?? 1,
        ofereceRefeicao: Boolean(vagaEdicao.ofereceRefeicao),
        vagaRelampago: Boolean(vagaEdicao.vagaRelampago),
      });
    } else {
      setForm(ESTADO_INICIAL);
    }
    setErro("");
  }, [vagaEdicao, aberto]);

  if (!aberto) return null;

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));
  };

const submeter = async (statusDestino) => {
    const valorNumerico = Number(String(form.valorHora).replace(",", "."));

    if (!form.titulo.trim() || !form.data || isNaN(valorNumerico) || valorNumerico <= 0) {
      setErro("Preencha título, data e um valor por hora maior que zero.");
      return;
    }

    setEnviando(true);
    setErro("");
    try {
      await onSalvar({
        ...form,
        intervaloMinutos: Number(form.intervaloMinutos),
        valorHora: valorNumerico,
        quantidadeVagas: Number(form.quantidadeVagas),
        status: statusDestino,
      });
      onClose();
    } catch {
      setErro("Não foi possível salvar a vaga. Tente novamente.");
    } finally {
      setEnviando(false);
    }
  };

    setEnviando(true);
    setErro("");
    try {
      await onSalvar({
        ...form,
        intervaloMinutos: Number(form.intervaloMinutos),
        valorHora: Number(form.valorHora),
        quantidadeVagas: Number(form.quantidadeVagas),
        status: statusDestino,
      });
      onClose();
    } catch {
      setErro("Não foi possível salvar a vaga. Tente novamente.");
    } finally {
      setEnviando(false);
    }
  };

  const tituloModal = somenteLeitura
    ? "Detalhes da vaga"
    : vagaEdicao
    ? "Editar vaga"
    : "Nova vaga";

  return (
    <div className="modal-overlay">
      <div className="modal-box">
        <div className="modal-header">
          <div>
            <div className="breadcrumb">Vagas / {tituloModal}</div>
            <h3>{tituloModal}</h3>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
            {vagaEdicao && !somenteLeitura && (
              <button
                type="button"
                className="btn-link danger"
                onClick={() => onSolicitarExclusao(vagaEdicao)}
              >
                Excluir vaga
              </button>
            )}
            <button type="button" className="btn-link" onClick={onClose}>
              <X size={20} />
            </button>
          </div>
        </div>

        {erro && (
          <p
            style={{
              backgroundColor: "#fee2e2",
              color: "#b91c1c",
              padding: "10px 12px",
              borderRadius: "8px",
              fontSize: "0.85rem",
              marginBottom: "14px",
            }}
          >
            {erro}
          </p>
        )}

        <div className="form-group">
          <label>Título da vaga</label>
          <input
            className="form-input"
            name="titulo"
            placeholder="Ex.: Garçom"
            value={form.titulo}
            onChange={handleChange}
            disabled={somenteLeitura}
          />
        </div>

        <div className="form-group">
          <label>Descrição</label>
          <textarea
            className="form-textarea"
            name="descricao"
            rows={3}
            placeholder="Atividades, requisitos e observações"
            value={form.descricao}
            onChange={handleChange}
            disabled={somenteLeitura}
          />
        </div>

        <div className="form-grid">
          <div className="form-group">
            <label>Data</label>
            <input
              type="date"
              className="form-input"
              name="data"
              value={form.data}
              onChange={handleChange}
              disabled={somenteLeitura}
            />
          </div>

          <div className="form-group">
            <label>Intervalo (minutos)</label>
            <input
              type="number"
              className="form-input"
              name="intervaloMinutos"
              min="0"
              value={form.intervaloMinutos}
              onChange={handleChange}
              disabled={somenteLeitura}
            />
          </div>

          <div className="form-group">
            <label>Início</label>
            <input
              type="time"
              className="form-input"
              name="horaInicio"
              value={form.horaInicio}
              onChange={handleChange}
              disabled={somenteLeitura}
            />
          </div>

          <div className="form-group">
            <label>Fim</label>
            <input
              type="time"
              className="form-input"
              name="horaFim"
              value={form.horaFim}
              onChange={handleChange}
              disabled={somenteLeitura}
            />
          </div>

          <div className="form-group">
            <label>Valor por hora (R$)</label>
            <input
              type="text"
              inputMode="decimal"
              className="form-input"
              name="valorHora"
              placeholder="0,00"
              value={form.valorHora}
              onChange={handleChange}
              disabled={somenteLeitura}
            />
          </div>

          <div className="form-group">
            <label>Nº de vagas</label>
            <input
              type="number"
              min="1"
              className="form-input"
              name="quantidadeVagas"
              value={form.quantidadeVagas}
              onChange={handleChange}
              disabled={somenteLeitura}
            />
          </div>
        </div>

        <div className="form-group">
          <label>Local</label>
          <input
            className="form-input"
            name="local"
            value={form.local}
            onChange={handleChange}
            disabled={somenteLeitura}
          />
        </div>

        <label className="checkbox-group">
          <input
            type="checkbox"
            name="ofereceRefeicao"
            checked={form.ofereceRefeicao}
            onChange={handleChange}
            disabled={somenteLeitura}
          />
          <span>Oferece refeição</span>
        </label>

        <label className="checkbox-group">
          <input
            type="checkbox"
            name="vagaRelampago"
            checked={form.vagaRelampago}
            onChange={handleChange}
            disabled={somenteLeitura}
          />
          <span>Vaga relâmpago (notifica freelas próximos)</span>
        </label>

        <div className="modal-footer">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={onClose}
            disabled={enviando}
          >
            {somenteLeitura ? "Fechar" : "Cancelar"}
          </button>

          {!somenteLeitura && (
            <>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={enviando}
                onClick={() =>
                  submeter(vagaEdicao ? vagaEdicao.status : "RASCUNHO")
                }
              >
                {vagaEdicao ? "Salvar alterações" : "Salvar rascunho"}
              </button>

              {(!vagaEdicao || vagaEdicao.status === "RASCUNHO") && (
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={enviando}
                  onClick={() => submeter("PUBLICADA")}
                >
                  Publicar vaga
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}