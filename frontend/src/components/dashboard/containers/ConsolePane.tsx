import { useEffect, useRef } from "react";
import { Terminal } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";
import { TERMINAL_THEME } from "../../../lib/terminalTheme.ts";

export default function ConsolePane({ containerId }: { containerId: string }) {
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!containerRef.current) return;

        const term = new Terminal({
            theme: TERMINAL_THEME,
            fontFamily: "ui-monospace, 'JetBrains Mono', monospace",
            fontSize: 13,
            cursorBlink: true,
            convertEol: true,
            scrollback: 5000,
        });
        const fitAddon = new FitAddon();
        term.loadAddon(fitAddon);
        term.open(containerRef.current);
        fitAddon.fit();
        term.focus();

        const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
        const ws = new WebSocket(`${proto}//${window.location.host}/ws/containers/${containerId}/console`);

        ws.onopen = () => {
            term.writeln("\x1b[90mAttached to the container's process. Input reaches its stdin directly — only works if the process was started with stdin open.\x1b[0m");
            term.focus();
        };
        ws.onmessage = (e) => term.write(e.data);
        ws.onerror = () => term.writeln("\r\n\x1b[31mConnection error.\x1b[0m");
        ws.onclose = () => term.writeln("\r\n\x1b[90mConnection closed.\x1b[0m");

        let lineBuffer = "";
        const dataDisposable = term.onData((data) => {
            for (const char of data) {
                if (char === "\r") {
                    term.write("\r\n");
                    if (ws.readyState === WebSocket.OPEN) {
                        ws.send(JSON.stringify({ type: "input", data: lineBuffer + "\n" }));
                    }
                    lineBuffer = "";
                } else if (char === "" || char === "\b") {
                    if (lineBuffer.length > 0) {
                        lineBuffer = lineBuffer.slice(0, -1);
                        term.write("\b \b");
                    }
                } else if (char >= " " || char === "\t") {
                    lineBuffer += char;
                    term.write(char);
                }
            }
        });

        const resizeObserver = new ResizeObserver(() => fitAddon.fit());
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
