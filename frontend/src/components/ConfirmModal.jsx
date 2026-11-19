import { X } from "lucide-react";

export function ConfirmModal({ tipo, vaga, onClose, onConfirm, carregando }) {
  if (!vaga || !tipo) return null;

  const isPublicar = tipo === "PUBLICAR";

  const formatarValor = (valor) =>
    Number(valor).toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });

  const formatarDataCurta = (dataISO) => {
    if (!dataISO) return "";
    const [, mes, dia] = dataISO.split("-");
    return `${dia}/${mes}`;
  };

  return (
    <div className="modal-overlay">
      <div className="modal-box small">
        <div className="modal-header">
          <h3>{isPublicar ? "Publicar vaga?" : "Excluir vaga?"}</h3>
          <button type="button" className="btn-link" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {isPublicar ? (
          <p style={{ color: "var(--text-secondary)", lineHeight: 1.5 }}>
            A vaga <strong>{vaga.titulo}</strong> ({formatarDataCurta(vaga.data)}{" "}
            {vaga.horaInicio.slice(0, 2)}h-{vaga.horaFim.slice(0, 2)}h{" "}
            {formatarValor(vaga.valorHora)}/h) ficará visível para freelas
            próximos, que poderão se candidatar. Você pode editar ou encerrar
            depois.
          </p>
        ) : (
          <p style={{ color: "var(--text-secondary)", lineHeight: 1.5 }}>
            A vaga <strong>{vaga.titulo}</strong> será removida. Esta ação não
            pode ser desfeita. Freelas já confirmados serão avisados do
            cancelamento.
          </p>
        )}

        <div className="modal-footer">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={onClose}
            disabled={carregando}
          >
            Cancelar
          </button>
          <button
            type="button"
            className={isPublicar ? "btn btn-primary" : "btn btn-danger"}
            onClick={onConfirm}
            disabled={carregando}
          >
            {carregando
              ? "Processando..."
              : isPublicar
              ? "Publicar agora"
              : "Sim, excluir"}
          </button>
        </div>
      </div>
    </div>
  );
}