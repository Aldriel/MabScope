/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { DataTable } from './DataTable';
import { Modal } from './Modal';
import { NumberField } from './NumberField';

const LETTERS = Array.from('ABCDEFGHIJKLMNOP');

/** Table of letters keeping its selection, as the views do. */
function Letters({ rows = LETTERS, onActivate = vi.fn() }) {
  const [selected, setSelected] = useState<string | null>(null);
  return (
    <DataTable
      label="Letters"
      rows={rows}
      rowKey={(row) => row}
      selected={selected}
      onSelect={setSelected}
      onActivate={onActivate}
      placeholder="No letters"
      columns={[
        { header: 'Letter', cell: (row) => row, width: '4em' },
        { header: 'Code', cell: (row) => row.charCodeAt(0), className: 'num' },
      ]}
    />
  );
}

const selected = () => screen.getByRole('row', { selected: true }).textContent;

describe('DataTable', () => {
  it('selects the rows with the mouse and the keyboard, and activates them', () => {
    const onActivate = vi.fn();
    const { container } = render(<Letters onActivate={onActivate} />);
    const table = container.querySelector('.data-table')!;

    fireEvent.keyDown(table, { key: 'Enter' });
    expect(onActivate).not.toHaveBeenCalled();
    fireEvent.keyDown(table, { key: 'ArrowDown' });
    expect(selected()).toBe('A65');
    fireEvent.keyDown(table, { key: 'PageDown' });
    expect(selected()).toBe('K75');
    fireEvent.keyDown(table, { key: 'ArrowUp' });
    expect(selected()).toBe('J74');
    fireEvent.keyDown(table, { key: 'End' });
    expect(selected()).toBe('P80');
    fireEvent.keyDown(table, { key: 'PageDown' });
    expect(selected()).toBe('P80');
    fireEvent.keyDown(table, { key: 'Home' });
    expect(selected()).toBe('A65');
    fireEvent.keyDown(table, { key: 'PageUp' });
    expect(selected()).toBe('A65');
    fireEvent.keyDown(table, { key: 'x' });

    fireEvent.mouseDown(screen.getByText('C'), { button: 2 });
    expect(selected()).toBe('A65');
    fireEvent.mouseDown(screen.getByText('C'));
    expect(selected()).toBe('C67');
    fireEvent.keyDown(table, { key: 'Enter' });
    expect(onActivate).toHaveBeenCalledWith('C');
    fireEvent.doubleClick(screen.getByText('D'));
    expect(onActivate).toHaveBeenLastCalledWith('D');
  });

  it('shows its placeholder and ignores the keys without rows', () => {
    const { container } = render(<Letters rows={[]} />);

    fireEvent.keyDown(container.querySelector('.data-table')!, { key: 'ArrowDown' });

    expect(screen.getByText('No letters')).toBeTruthy();
    expect(screen.queryByRole('row', { selected: true })).toBeNull();
  });

  it('resizes a column by dragging the edge of its header', () => {
    const { container } = render(<Letters />);
    const table = container.querySelector('table')!;
    const resizer = container.querySelectorAll('.column-resizer')[0];

    fireEvent.mouseDown(resizer, { button: 2, clientX: 0 });
    expect(document.body.classList.contains('resizing')).toBe(false);
    fireEvent.mouseDown(resizer, { button: 0, clientX: 0 });
    expect(document.body.classList.contains('resizing')).toBe(true);
    fireEvent.mouseMove(window, { clientX: 80 });
    fireEvent.mouseUp(window);

    expect(document.body.classList.contains('resizing')).toBe(false);
    expect(table.className).toBe('resized');
    expect(table.style.width).toBe('80px');
    expect(container.querySelector('th')?.style.width).toBe('80px');

    fireEvent.mouseMove(window, { clientX: 300 });
    expect(table.style.width).toBe('80px');
    fireEvent.doubleClick(resizer);
    expect(table.className).toBe('');
    expect(container.querySelector('th')?.style.width).toBe('4em');
  });
});

describe('NumberField', () => {
  function renderField(step?: number) {
    const onChange = vi.fn();
    render(
      <NumberField value={5} min={1} max={10} step={step} label="Count" onChange={onChange} />,
    );
    return { field: screen.getByLabelText('Count') as HTMLInputElement, onChange };
  }

  it('commits the arrows at once and typed values when they are confirmed', () => {
    const { field, onChange } = renderField();

    fireEvent.change(field, { target: { value: '6' } });
    expect(onChange).toHaveBeenLastCalledWith(6);

    fireEvent.change(field, { target: { value: '9' } });
    expect(onChange).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(field, { key: 'a' });
    fireEvent.keyDown(field, { key: 'Enter' });
    expect(onChange).toHaveBeenLastCalledWith(9);

    fireEvent.change(field, { target: { value: '99' } });
    fireEvent.blur(field);
    expect(onChange).toHaveBeenLastCalledWith(10);
  });

  it('keeps the value when the text is not a valid number', () => {
    const { field, onChange } = renderField();

    fireEvent.change(field, { target: { value: '7.5' } });
    fireEvent.blur(field);
    fireEvent.change(field, { target: { value: '' } });
    fireEvent.blur(field);

    expect(onChange).not.toHaveBeenCalled();
    expect(field.value).toBe('5');
  });

  it('accepts decimals with a step below one', () => {
    const { field, onChange } = renderField(0.5);

    fireEvent.change(field, { target: { value: '5.5' } });
    expect(onChange).toHaveBeenLastCalledWith(5.5);
  });
});

describe('Modal', () => {
  it('focuses its first button and closes on Escape or a click outside', () => {
    const onClose = vi.fn();
    render(
      <Modal title="Title" header="Header" icon="!" onClose={onClose} buttons={<button>OK</button>}>
        Content
      </Modal>,
    );

    expect(document.activeElement?.textContent).toBe('OK');
    expect(screen.getByText('!')).toBeTruthy();
    fireEvent.mouseDown(screen.getByText('Content'));
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.mouseDown(document.querySelector('.modal-backdrop')!);
    fireEvent.keyDown(window, { key: 'Enter' });
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
