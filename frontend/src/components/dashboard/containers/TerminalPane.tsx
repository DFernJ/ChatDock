import { useEffect, useRef } from "react";
import { Terminal } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";
import { TERMINAL_THEME } from "../../../lib/terminalTheme.ts";

export default function TerminalPane({ containerId }: { containerId: string }) {
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!containerRef.current) return;

        const term = new Terminal({
            theme: TERMINAL_THEME,
            fontFamily: "ui-monospace, 'JetBrains Mono', monospace",
            fontSize: 13,
            cursorBlink: true,
            convertEol: true,
        });
        const fitAddon = new FitAddon();
        term.loadAddon(fitAddon);
        term.open(containerRef.current);
        fitAddon.fit();
        term.focus();

        const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
        const ws = new WebSocket(`${proto}//${window.location.host}/ws/containers/${containerId}/terminal`);

        const sendResize = () => {
            if (ws.readyState !== WebSocket.OPEN) return;
            ws.send(JSON.stringify({ type: "resize", cols: term.cols, rows: term.rows }));
        };

        ws.onopen = () => {
            fitAddon.fit();
            sendResize();
            term.writeln("\x1b[90mConnected.\x1b[0m");
            term.focus();
        };
        ws.onmessage = (e) => term.write(e.data);
        ws.onerror = () => term.writeln("\r\n\x1b[31mConnection error.\x1b[0m");
        ws.onclose = () => term.writeln("\r\n\x1b[90mConnection closed.\x1b[0m");

        const dataDisposable = term.onData((data) => {
            if (ws.readyState !== WebSocket.OPEN) return;
            ws.send(JSON.stringify({ type: "input", data }));
        });

        const resizeObserver = new ResizeObserver(() => {
            fitAddon.fit();
            sendResize();
        });
        resizeObserver.observe(containerRef.current);

        return () => {
            resizeObserver.disconnect();
            dataDisposable.dispose();
            ws.close();
            term.dispose();
        };
    }, [containerId]);

    return <div ref={containerRef} className="h-full w-full" />;
}
