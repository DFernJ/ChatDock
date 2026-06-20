import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./context/AuthContext.tsx";
import LoginPage from "./pages/LoginPage.tsx";
import AdminPage from "./pages/AdminPage.tsx";
import ProfilePage from "./pages/ProfilePage.tsx";
import LegalPage from "./pages/LegalPage.tsx";
import TermsPage from "./pages/TermsPage.tsx";

const PUBLIC_PATHS = ["/legal", "/terms"];

export default function App() {
    const { status, user } = useAuth();
    const location = useLocation();

    if (status === "loading") {
        return <div className="min-h-screen bg-stage grid place-items-center">
            <span className="text-ink-400 font-mono text-xs">cargando sesión…</span>
        </div>;
    }

    if (status === "no-auth" && location.pathname !== "/" && !PUBLIC_PATHS.includes(location.pathname)) {
        return <Navigate to="/" replace />;
    }

  return (
      <Routes>
          <Route path={"/"} element={status === "auth" ? <ProfilePage /> : <LoginPage />} />
          <Route path={"/admin"} element={
              status === "auth" && user?.authRole === "admin" ? <AdminPage /> : <Navigate to="/" replace />
          } />
          <Route path={"/legal"} element={<LegalPage />} />
          <Route path={"/terms"} element={<TermsPage />} />
          <Route path={"/profile"} element={<ProfilePage />} />
      </Routes>
  )
}