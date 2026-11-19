import { useState, useEffect } from "react";
import { Plus } from "lucide-react";
import { vagaService } from "../services/vagaService";
import { VagaFormModal } from "../components/VagaFormModal";
import { ConfirmModal } from "../components/ConfirmModal";

const DIAS_SEMANA = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"];

export function VagasPage() {
  const [vagas, setVagas] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [busca, setBusca] = useState("");
  const [filtroStatus, setFiltroStatus] = useState("TODOS");

  const [modalFormAberto, setModalFormAberto] = useState(false);
  const [vagaEdicao, setVagaEdicao] = useState(null);
  const [somenteLeitura, setSomenteLeitura] = useState(false);
  const [confirmacao, setConfirmacao] = useState({ tipo: null, vaga: null });
  const [processandoAcao, setProcessandoAcao] = useState(false);

  const carregarVagas = async () => {
    setCarregando(true);
    const dados = await vagaService.listar();
    setVagas(dados);
    setCarregando(false);
  };

  useEffect(() => {
    carregarVagas();
  }, []);

  const formatarDataHorario = (vaga) => {
    if (!vaga.data) return "";
    const [ano, mes, dia] = vaga.data.split("-").map(Number);
    const dataObj = new Date(ano, mes - 1, dia);
    const diaSemana = DIAS_SEMANA[dataObj.getDay()];
    const diaFormatado = String(dia).padStart(2, "0");
    const mesFormatado = String(mes).padStart(2, "0");
    const inicio = vaga.horaInicio ? `${Number(vaga.horaInicio.slice(0, 2))}h` : "";
    const fim = vaga.horaFim ? `${Number(vaga.horaFim.slice(0, 2))}h` : "";

    return `${diaSemana} ${diaFormatado}/${mesFormatado} ${inicio}-${fim}`;
  };

  const formatarValor = (valor) =>
    Number(valor).toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });

  const abrirNovaVaga = () => {
    setVagaEdicao(null);
    setSomenteLeitura(false);
    setModalFormAberto(true);
  };

  const abrirEditarVaga = (vaga) => {
    setVagaEdicao(vaga);
    setSomenteLeitura(false);
    setModalFormAberto(true);
  };

  const abrirVerVaga = (vaga) => {
    setVagaEdicao(vaga);
    setSomenteLeitura(true);
    setModalFormAberto(true);
  };

  const handleSalvarVaga = async (dados) => {
    if (vagaEdicao) {
      await vagaService.editar(vagaEdicao.id, dados);
    } else {
      await vagaService.criar(dados);
    }
    await carregarVagas();
  };

  const handleEncerrarVaga = async (vaga) => {
    await vagaService.alterarStatus(vaga.id, "ENCERRADA");
    await carregarVagas();
  };

  const handleConfirmarModal = async () => {
    if (!confirmacao.vaga || !confirmacao.tipo) return;
    setProcessandoAcao(true);
    try {
      if (confirmacao.tipo === "PUBLICAR") {
        await vagaService.alterarStatus(confirmacao.vaga.id, "PUBLICADA");
      } else if (confirmacao.tipo === "EXCLUIR") {
        await vagaService.excluir(confirmacao.vaga.id);
        setModalFormAberto(false);
      }
      setConfirmacao({ tipo: null, vaga: null });
      await carregarVagas();
    } finally {
      setProcessandoAcao(false);
    }
  };

  const vagasFiltradas = vagas.filter((vaga) => {
    const bateTitulo = vaga.titulo
      .toLowerCase()
      .includes(busca.toLowerCase().trim());
    const bateStatus =
      filtroStatus === "TODOS" || vaga.status === filtroStatus;
    return bateTitulo && bateStatus;
  });

  const renderBadgeStatus = (status) => {
    const mapa = {
      PUBLICADA: { texto: "Publicada", classe: "badge-publicada" },
      RASCUNHO: { texto: "Rascunho", classe: "badge-rascunho" },
      ENCERRADA: { texto: "Encerrada", classe: "badge-encerrada" },
    };
    const info = mapa[status] || { texto: status, classe: "badge-rascunho" };
    return <span className={`badge ${info.classe}`}>{info.texto}</span>;
  };

  return (
    <main className="main-content">
      <div className="breadcrumb">Painel / Vagas</div>

      <div className="page-header">
        <h1 className="page-title">Vagas</h1>
        <button type="button" className="btn btn-primary" onClick={abrirNovaVaga}>
          <Plus size={18} />
          <span>Nova vaga</span>
        </button>
      </div>

      <div className="filters-bar">
        <input
          type="text"
          className="search-input"
          placeholder="Buscar por título"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />

        <select
          className="select-input"
          value={filtroStatus}
          onChange={(e) => setFiltroStatus(e.target.value)}
        >
          <option value="TODOS">Status: todos</option>
          <option value="PUBLICADA">Status: Publicada</option>
          <option value="RASCUNHO">Status: Rascunho</option>
          <option value="ENCERRADA">Status: Encerrada</option>
        </select>
      </div>

      <div className="table-card">
        {carregando ? (
          <p style={{ padding: "24px", color: "var(--text-secondary)" }}>
            Carregando vagas...
          </p>
        ) : vagasFiltradas.length === 0 ? (
          <p style={{ padding: "24px", color: "var(--text-secondary)" }}>
            Nenhuma vaga encontrada com os filtros atuais.
          </p>
        ) : (
          <table className="vagas-table">
            <thead>
              <tr>
                <th>Vaga</th>
                <th>Data e Horário</th>
                <th>Valor/h</th>
                <th>Preenchidas</th>
                <th>Status</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {vagasFiltradas.map((vaga) => (
                <tr key={vaga.id}>
                  <td style={{ fontWeight: 600 }}>{vaga.titulo}</td>
                  <td>{formatarDataHorario(vaga)}</td>
                  <td>{formatarValor(vaga.valorHora)}</td>
                  <td>
                    {vaga.preenchidas} de {vaga.quantidadeVagas}
                  </td>
                  <td>{renderBadgeStatus(vaga.status)}</td>
                  <td>
                    {vaga.status === "PUBLICADA" && (
                      <>
                        <button
                          type="button"
                          className="btn-link"
                          onClick={() => abrirEditarVaga(vaga)}
                        >
                          Editar
                        </button>
                        <button
                          type="button"
                          className="btn-link"
                          onClick={() => handleEncerrarVaga(vaga)}
                        >
                          Encerrar
                        </button>
                        <button
                          type="button"
                          className="btn-link danger"
                          onClick={() =>
                            setConfirmacao({ tipo: "EXCLUIR", vaga })
                          }
                        >
                          Excluir
                        </button>
                      </>
                    )}

                    {vaga.status === "RASCUNHO" && (
                      <>
                        <button
                          type="button"
                          className="btn-link"
                          onClick={() => abrirEditarVaga(vaga)}
                        >
                          Editar
                        </button>
                        <button
                          type="button"
                          className="btn-link publish"
                          onClick={() =>
                            setConfirmacao({ tipo: "PUBLICAR", vaga })
                          }
                        >
                          Publicar
                        </button>
                        <button
                          type="button"
                          className="btn-link danger"
                          onClick={() =>
                            setConfirmacao({ tipo: "EXCLUIR", vaga })
                          }
                        >
                          Excluir
                        </button>
                      </>
                    )}

                    {vaga.status === "ENCERRADA" && (
                      <>
                        <button
                          type="button"
                          className="btn-link"
                          onClick={() => abrirVerVaga(vaga)}
                        >
                          Ver
                        </button>
                        <button
                          type="button"
                          className="btn-link danger"
                          onClick={() =>
                            setConfirmacao({ tipo: "EXCLUIR", vaga })
                          }
                        >
                          Excluir
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <VagaFormModal
        aberto={modalFormAberto}
        vagaEdicao={vagaEdicao}
        somenteLeitura={somenteLeitura}
        onClose={() => setModalFormAberto(false)}
        onSalvar={handleSalvarVaga}
        onSolicitarExclusao={(vaga) =>
          setConfirmacao({ tipo: "EXCLUIR", vaga })
        }
      />

      <ConfirmModal
        tipo={confirmacao.tipo}
        vaga={confirmacao.vaga}
        carregando={processandoAcao}
        onClose={() => setConfirmacao({ tipo: null, vaga: null })}
        onConfirm={handleConfirmarModal}
      />
    </main>
  );
}