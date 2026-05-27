import { Route, Routes } from "react-router-dom";
import { useAuth } from "./context/AuthContext.tsx";
import LoginPage from "./pages/LoginPage.tsx";
import HomepagePage from "./pages/HomepagePage.tsx";

export default function App() {
    const { status } = useAuth();

    if (status === "loading") {
        return <div className="min-h-screen bg-stage grid place-items-center">
            <span className="text-ink-400 font-mono text-xs">cargando sesión…</span>
        </div>;
    }

  return (
      <Routes>
          <Route path={"/"} element={<LoginPage />} />
          <Route path={"/dashboard"} element={<HomepagePage />} />
      </Routes>
  )
}