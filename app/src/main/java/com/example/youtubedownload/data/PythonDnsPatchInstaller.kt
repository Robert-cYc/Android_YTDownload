package com.example.youtubedownload.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Installs a Python `sitecustomize.py` hook into the extracted youtubedl-android Python runtime
 * so that if Android's Bionic `getaddrinfo` fails inside the spawned Python process with
 * `[Errno 7] No address associated with hostname`, Python falls back to JVM-resolved hosts,
 * direct UDP/TCP DNS (8.8.8.8 / 1.1.1.1), and DNS-over-HTTPS.
 */
object PythonDnsPatchInstaller {

    private const val TAG = "PythonDnsPatch"

    private val PRE_RESOLVE_HOSTS = listOf(
        "www.youtube.com",
        "youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "youtubei.googleapis.com",
        "i.ytimg.com",
        "s.ytimg.com",
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com"
    )

    fun install(context: Context) {
        try {
            val baseDir = File(context.noBackupFilesDir, "youtubedl-android")
            if (!baseDir.exists()) return

            val dnsCacheFile = File(baseDir, "dns_cache.json")
            val scriptContent = buildSiteCustomizeScript(dnsCacheFile.absolutePath)

            baseDir.walkTopDown()
                .filter { it.isDirectory && (it.name.startsWith("python3.") || it.name == "site-packages") }
                .forEach { dir ->
                    try {
                        File(dir, "sitecustomize.py").writeText(scriptContent)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed writing sitecustomize.py to ${dir.absolutePath}", t)
                    }
                }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to install Python DNS fallback patch", t)
        }
    }

    fun refreshHostCache(context: Context) {
        try {
            val baseDir = File(context.noBackupFilesDir, "youtubedl-android").apply { mkdirs() }
            val dnsCacheFile = File(baseDir, "dns_cache.json")
            val json = if (dnsCacheFile.exists()) {
                runCatching { JSONObject(dnsCacheFile.readText()) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }

            for (host in PRE_RESOLVE_HOSTS) {
                try {
                    val ipv4 = InetAddress.getAllByName(host)
                        .firstOrNull { it is Inet4Address }
                        ?.hostAddress
                    if (!ipv4.isNullOrBlank()) {
                        json.put(host, ipv4)
                    }
                } catch (_: Throwable) {
                    // Ignore individual lookup errors; Python UDP/DoH fallback will handle it
                }
            }
            dnsCacheFile.writeText(json.toString())
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to refresh DNS host cache", t)
        }
    }

    private fun buildSiteCustomizeScript(dnsCacheFilePath: String): String {
        val escapedPath = dnsCacheFilePath.replace("\\", "\\\\")
        return """
import json
import os
import random
import socket
import ssl
import struct
import urllib.request

_orig_getaddrinfo = socket.getaddrinfo
_dns_cache = {}
_CACHE_FILE = "$escapedPath"

def _load_file_cache():
    try:
        if os.path.exists(_CACHE_FILE):
            with open(_CACHE_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                if isinstance(data, dict):
                    _dns_cache.update(data)
    except Exception:
        pass

_load_file_cache()

def _is_ipv4_literal(host):
    parts = str(host).split(".")
    return len(parts) == 4 and all(p.isdigit() and 0 <= int(p) <= 255 for p in parts)

def _build_dns_query(hostname):
    tid = random.randint(1, 65534)
    header = struct.pack("!HHHHHH", tid, 0x0100, 1, 0, 0, 0)
    qname = b"".join(bytes([len(p)]) + p.encode("ascii") for p in hostname.strip(".").split(".")) + b"\x00"
    return header + qname + struct.pack("!HH", 1, 1)

def _parse_dns_response(data):
    def read_name(buf, offset):
        labels, jumped, orig_off = [], False, offset
        while True:
            length = buf[offset]
            if length == 0:
                offset += 1
                break
            if (length & 0xC0) == 0xC0:
                ptr = ((length & 0x3F) << 8) | buf[offset + 1]
                if not jumped:
                    orig_off = offset + 2
                    jumped = True
                offset = ptr
            else:
                offset += 1
                labels.append(buf[offset:offset+length].decode("ascii", "ignore"))
                offset += length
        return ".".join(labels), (orig_off if jumped else offset)

    _, _, qdcount, ancount, _, _ = struct.unpack("!HHHHHH", data[:12])
    off = 12
    for _ in range(qdcount):
        _, off = read_name(data, off)
        off += 4
    for _ in range(ancount):
        _, off = read_name(data, off)
        rtype, _, _, rdlen = struct.unpack("!HHIH", data[off:off+10])
        off += 10
        if rtype == 1 and rdlen == 4:
            return socket.inet_ntoa(data[off:off+4])
        off += rdlen
    return None

def _resolve_udp_or_tcp(hostname):
    packet = _build_dns_query(hostname)
    for srv in ("8.8.8.8", "1.1.1.1", "9.9.9.9"):
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                s.settimeout(2.0)
                s.sendto(packet, (srv, 53))
                data, _ = s.recvfrom(2048)
            ip = _parse_dns_response(data)
            if ip:
                return ip
        except Exception:
            pass
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.settimeout(2.5)
                s.connect((srv, 53))
                s.sendall(struct.pack("!H", len(packet)) + packet)
                resp_len_bytes = s.recv(2)
                if len(resp_len_bytes) == 2:
                    resp_len = struct.unpack("!H", resp_len_bytes)[0]
                    data = b""
                    while len(data) < resp_len:
                        chunk = s.recv(resp_len - len(data))
                        if not chunk:
                            break
                        data += chunk
                    ip = _parse_dns_response(data)
                    if ip:
                        return ip
        except Exception:
            pass
    return None

def _resolve_doh(hostname):
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    for url in (
        f"https://1.1.1.1/dns-query?name={hostname}&type=A",
        f"https://8.8.8.8/resolve?name={hostname}&type=A",
    ):
        try:
            req = urllib.request.Request(url, headers={"Accept": "application/dns-json"})
            with urllib.request.urlopen(req, timeout=3.0, context=ctx) as resp:
                body = json.loads(resp.read().decode("utf-8"))
                for ans in body.get("Answer", []):
                    if ans.get("type") == 1 and ans.get("data"):
                        return ans["data"]
        except Exception:
            continue
    return None

def _patched_getaddrinfo(host, port, family=0, type=0, proto=0, flags=0):
    try:
        return _orig_getaddrinfo(host, port, family, type, proto, flags)
    except socket.gaierror:
        if family != socket.AF_INET:
            try:
                return _orig_getaddrinfo(host, port, socket.AF_INET, type, proto, flags)
            except socket.gaierror:
                pass
        h = host.decode("ascii", "ignore") if isinstance(host, bytes) else str(host)
        if not h or _is_ipv4_literal(h):
            raise
        _load_file_cache()
        ip = _dns_cache.get(h) or _resolve_udp_or_tcp(h) or _resolve_doh(h)
        if ip:
            _dns_cache[h] = ip
            return _orig_getaddrinfo(ip, port, socket.AF_INET, type, proto, flags)
        raise

socket.getaddrinfo = _patched_getaddrinfo
""".trimIndent()
    }
}
