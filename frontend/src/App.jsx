import { NavLink, Routes, Route, Navigate } from "react-router-dom";
import CreditRequest from "./pages/CreditRequest";
import ApprovalDesk from "./pages/ApprovalDesk";

function Nav() {
  return (
    <header className="nav">
      <div className="nav-inner">
        <div className="nav-logo">Genesis / Crédito</div>
        <nav className="nav-links">
          <NavLink to="/" className={({ isActive }) => "nav-link" + (isActive ? " active" : "")} end>Solicitar</NavLink>
          <NavLink to="/desk" className={({ isActive }) => "nav-link" + (isActive ? " active" : "")}>Mesa Humana</NavLink>
        </nav>
      </div>
    </header>
  );
}

export default function App() {
  return (
    <>
      <Nav />
      <main className="container page">
        <Routes>
          <Route path="/" element={<CreditRequest />} />
          <Route path="/desk" element={<ApprovalDesk />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  );
}
