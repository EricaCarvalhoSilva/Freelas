import { LayoutDashboard, Briefcase, Users, Star, Settings } from "lucide-react";

export function Sidebar() {
  const menuItems = [
    { label: "Painel", icon: LayoutDashboard, active: false },
    { label: "Vagas", icon: Briefcase, active: true },
    { label: "Candidatos", icon: Users, active: false },
    { label: "Avaliações", icon: Star, active: false },
  ];

  return (
    <aside className="sidebar">
      <div>
        <div className="sidebar-brand">MeuFreela</div>
        <nav className="sidebar-nav">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.label}
                type="button"
                className={`nav-item ${item.active ? "active" : ""}`}
              >
                <Icon size={18} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </div>

      <button type="button" className="nav-item">
        <Settings size={18} />
        <span>Configurações</span>
      </button>
    </aside>
  );
}