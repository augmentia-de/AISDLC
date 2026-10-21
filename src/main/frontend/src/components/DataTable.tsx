import type { ReactNode } from 'react';

/**
 * Die zugängliche Datentabelle.
 *
 * <strong>Echte Tabelle</strong> (ADR-018 §4): `<table>` mit `<caption>`,
 * `<th scope="col">` und optional `<th scope="row">`. Nur so ansagen
 * Screenreader „Tabelle mit Zeilen und Spalten“ und erlauben das
 * sprungweise Bedienen. Ein Div-Grid waere semantisch ein anderes Element.
 *
 * Die Tabelle wird <strong>nur</strong> gerendert, wenn Zeilen vorhanden
 * sind — eine leere Tabelle ohne Erklaerung ist der Leerzustand
 * (AC-020-3).
 */

/** Eine Spalte der Tabelle. */
export interface TableColumn<T> {
  /** Der Text der Spaltenueberschrift. */
  header: string;
  /** Der Inhalt der Zelle. */
  render: (row: T) => ReactNode;
  /** Der Zellinhalt ist der Zeilenkopf (`th scope="row"`). */
  rowHeader?: boolean;
}

/** Die Eigenschaften der Tabelle. */
export interface DataTableProps<T> {
  /** Der Name der Tabelle — der `<caption>`. */
  caption: string;
  /** Die Spalten. */
  columns: ReadonlyArray<TableColumn<T>>;
  /** Die Zeilen. */
  rows: readonly T[];
  /** Der Schluessel einer Zeile — er muss eindeutig sein. */
  rowKey: (row: T) => string;
}

/**
 * Zeigt die Tabelle.
 *
 * @param props die Eigenschaften der Tabelle
 * @returns die Tabelle
 */
export function DataTable<T>({ caption, columns, rows, rowKey }: DataTableProps<T>) {
  return (
    <table className="data-table">
      <caption className="data-table__caption">{caption}</caption>
      <thead>
        <tr>
          {columns.map((column) => (
            <th key={column.header} scope="col">
              {column.header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={rowKey(row)}>
            {columns.map((column) =>
              column.rowHeader === true ? (
                <th key={column.header} scope="row">
                  {column.render(row)}
                </th>
              ) : (
                <td key={column.header}>{column.render(row)}</td>
              ),
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
