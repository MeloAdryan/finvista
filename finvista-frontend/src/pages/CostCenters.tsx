import { useState } from "react";
import ExpenseExplorer from "../components/ExpenseExplorer";
import CostClassificationEditor from "../components/CostClassificationEditor";
import "../styles/dashboard.css";
export default function CostCenters() {
  const [revision, setRevision] = useState(0);
  return (
    <div className="dashboard">
      <ExpenseExplorer prioridade="centros" revision={revision} />
      <CostClassificationEditor onSaved={() => setRevision((v) => v + 1)} />
    </div>
  );
}
