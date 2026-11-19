import { Sidebar } from "./components/Sidebar";
import { VagasPage } from "./pages/VagasPage";

export function App() {
  return (
    <div className="app-layout">
      <Sidebar />
      <VagasPage />
    </div>
  );
}

export default App;