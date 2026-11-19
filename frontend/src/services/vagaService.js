const STORAGE_KEY = "@meufreela:vagas";

const VAGAS_INICIAIS = [
  {
    id: 1,
    titulo: "Garçom",
    descricao: "Atendimento no salão e apoio nas mesas.",
    data: "2026-11-22",
    horaInicio: "18:00",
    horaFim: "23:00",
    intervaloMinutos: 30,
    local: "Endereço da empresa (padrão)",
    valorHora: 18.0,
    quantidadeVagas: 3,
    preenchidas: 2,
    ofereceRefeicao: true,
    vagaRelampago: false,
    status: "PUBLICADA",
  },
  {
    id: 2,
    titulo: "Cozinheiro auxiliar",
    descricao: "Apoio na cozinha: preparo de ingredientes e limpeza da bancada.",
    data: "2026-11-21",
    horaInicio: "11:00",
    horaFim: "16:00",
    intervaloMinutos: 30,
    local: "Endereço da empresa (padrão)",
    valorHora: 22.0,
    quantidadeVagas: 1,
    preenchidas: 0,
    ofereceRefeicao: true,
    vagaRelampago: false,
    status: "RASCUNHO",
  },
  {
    id: 3,
    titulo: "Operador de caixa",
    descricao: "Abertura, fechamento e atendimento no caixa.",
    data: "2026-11-23",
    horaInicio: "08:00",
    horaFim: "14:00",
    intervaloMinutos: 30,
    local: "Endereço da empresa (padrão)",
    valorHora: 17.5,
    quantidadeVagas: 1,
    preenchidas: 1,
    ofereceRefeicao: false,
    vagaRelampago: false,
    status: "ENCERRADA",
  },
  {
    id: 4,
    titulo: "Auxiliar de limpeza",
    descricao: "Higienização da cozinha e salão antes da abertura.",
    data: "2026-11-24",
    horaInicio: "06:00",
    horaFim: "10:00",
    intervaloMinutos: 15,
    local: "Endereço da empresa (padrão)",
    valorHora: 15.0,
    quantidadeVagas: 2,
    preenchidas: 0,
    ofereceRefeicao: false,
    vagaRelampago: false,
    status: "RASCUNHO",
  },
];

//SIMULA ATRASO ASSINCRONO DE UMA REQUISIÇÃO, APENAS TESTE, IMPLEMENTAR COM BACKEND DEPOIS, NÃO TOQUE NESTA PARTE POR FAVOR PELO AMOR DE DEUS"//
const delay = (ms = 200) => new Promise((resolve) => setTimeout(resolve, ms));

const lerBancoLocal = () => {
  const salvo = localStorage.getItem(STORAGE_KEY);
  if (!salvo) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(VAGAS_INICIAIS));
    return VAGAS_INICIAIS;
  }
  return JSON.parse(salvo);
};

export const vagaService = {
  async listar() {
    await delay();
    return lerBancoLocal();
  },

  async criar(dadosVaga) {
    await delay();
    const vagas = lerBancoLocal();
    const novaVaga = {
      ...dadosVaga,
      id: Date.now(),
      preenchidas: 0,
    };
    const listaAtualizada = [novaVaga, ...vagas];
    localStorage.setItem(STORAGE_KEY, JSON.stringify(listaAtualizada));
    return novaVaga;
  },

  async editar(id, dadosAtualizados) {
    await delay();
    const vagas = lerBancoLocal();
    const listaAtualizada = vagas.map((vaga) =>
      vaga.id === id ? { ...vaga, ...dadosAtualizados } : vaga
    );
    localStorage.setItem(STORAGE_KEY, JSON.stringify(listaAtualizada));
    return listaAtualizada.find((vaga) => vaga.id === id);
  },

  async alterarStatus(id, novoStatus) {
    return this.editar(id, { status: novoStatus });
  },

  async excluir(id) {
    await delay();
    const vagas = lerBancoLocal();
    const listaFiltrada = vagas.filter((vaga) => vaga.id !== id);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(listaFiltrada));
  },
};