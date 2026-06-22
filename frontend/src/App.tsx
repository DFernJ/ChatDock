import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./context/AuthContext.tsx";
import { Topbar } from "./components/Topbar.tsx";
import LoginPage from "./pages/LoginPage.tsx";
import AdminPage from "./pages/AdminPage.tsx";
import DashboardPage from "./pages/DashboardPage.tsx";
import ImportContainerPage from "./pages/ImportContainerPage.tsx";
import ProfilePage from "./pages/ProfilePage.tsx";
import LegalPage from "./pages/LegalPage.tsx";
import TermsPage from "./pages/TermsPage.tsx";

const PUBLIC_PATHS = ["/legal", "/terms"];

function AuthedLayout() {
    return (
        <div className="relative min-h-screen w-full bg-stage bg-grid overflow-hidden">
            <Topbar variant="auth" />
            <Outlet />
        </div>
    );
}

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
          {status === "auth" ? (
              <Route element={<AuthedLayout />}>
                  <Route path={"/"} element={<DashboardPage />} />
                  <Route path={"/admin"} element={user?.authRole === "admin" ? <AdminPage /> : <Navigate to="/" replace />} />
                  <Route path={"/import"} element={<ImportContainerPage />} />
                  <Route path={"/profile"} element={<ProfilePage />} />
              </Route>
          ) : (
              <Route path={"/"} element={<LoginPage />} />
          )}
          <Route path={"/legal"} element={<LegalPage />} />
          <Route path={"/terms"} element={<TermsPage />} />
      </Routes>
  )
}
