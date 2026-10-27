// scene-rs: Scene 复刻版的长时采集守护二进制
// 子命令:
//   fps record --interval-ms 500 --out FILE [--surface NAME]   帧率采样写 JSONL
//   power log   --interval-ms 5000 --out FILE                   电源采样写 JSONL
//   dump fps|power|mem                                          单次 JSON 输出
//   stop FILE.pid                                               向 pidfile 记录的进程发 TERM
use serde::Serialize;
use std::fs;
use std::io::Write;
use std::os::unix::process::CommandExt;
use std::path::Path;
use std::process::{Command, Stdio};
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn read_trim(p: &str) -> Option<String> {
    fs::read_to_string(p).ok().map(|s| s.trim().to_string())
}

fn read_i64(p: &str) -> Option<i64> {
    read_trim(p)?.parse().ok()
}

// ---------- 数据模型 ----------

#[derive(Serialize)]
struct FpsSample {
    t: u64,
    fps: f32,          // 窗口内帧率
    frames: u32,       // 窗口内帧数
    avg_frame_ms: f32, // 平均帧间隔
    jitter_ms: f32,    // 帧间隔标准差
}

#[derive(Serialize)]
struct PowerSample {
    t: u64,
    capacity: i32,      // %
    current_ua: i64,    // µA（放电为负）
    voltage_uv: i64,    // µV
    power_w: f32,       // |U*I|
    temp_deci: i32,     // 0.1°C
    status: String,
}

#[derive(Serialize)]
struct MemDump {
    total_kb: u64,
    avail_kb: u64,
    swap_total_kb: u64,
    swap_free_kb: u64,
}

// ---------- 帧率 ----------

/// 解析 dumpsys SurfaceFlinger --latency <layer> 的 presentTime 序列（ns）。
/// 行格式: desired present ready；INT64_MAX 表示 pending。
fn parse_latency(out: &str) -> Vec<u64> {
    let mut v = Vec::new();
    for line in out.lines().skip(1) {
        let cols: Vec<&str> = line.split_whitespace().collect();
        if cols.len() >= 2 {
            if let Ok(p) = cols[1].parse::<i64>() {
                if p > 0 && p != i64::MAX {
                    v.push(p as u64);
                }
            }
        }
    }
    v.sort_unstable();
    v
}

/// 找出最活跃的 layer（默认取合成器主屏 layer）
fn find_active_layer() -> Option<String> {
    let out = Command::new("dumpsys")
        .args(["SurfaceFlinger", "--list"])
        .output()
        .ok()?
        .stdout;
    let text = String::from_utf8_lossy(&out);
    // 优先选含 SurfaceView/主屏特征、行数最短的常见 layer
    let mut best: Option<(usize, String)> = None;
    for line in text.lines().rev() {
        let l = line.trim();
        if l.is_empty() || l.len() > 200 {
            continue;
        }
        if l.contains("SurfaceView") || l.starts_with("com.android") || l.contains("MainActivity") {
            return Some(l.to_string());
        }
        if best.is_none() {
            best = Some((l.len(), l.to_string()));
        }
    }
    best.map(|(_, s)| s)
}

fn sample_fps(surface: Option<&str>) -> Option<FpsSample> {
    let layer = match surface {
        Some(s) => s.to_string(),
        None => find_active_layer()?,
    };
    let out = Command::new("dumpsys")
        .args(["SurfaceFlinger", "--latency", &layer])
        .output()
        .ok()?;
    let stamps = parse_latency(&String::from_utf8_lossy(&out.stdout));
    if stamps.len() < 2 {
        return None;
    }
    // 取最近 1s 窗口
    let last = *stamps.last().unwrap();
    let cutoff = last.saturating_sub(1_000_000_000);
    let window: Vec<u64> = stamps.into_iter().filter(|t| *t >= cutoff).collect();
    if window.len() < 2 {
        return Some(FpsSample { t: now_ms(), fps: 0.0, frames: 0, avg_frame_ms: 0.0, jitter_ms: 0.0 });
    }
    let n = window.len();
    let span_ms = (window[n - 1] - window[0]) as f32 / 1e6;
    let mut deltas: Vec<f32> = Vec::with_capacity(n - 1);
    for i in 1..n {
        deltas.push((window[i] - window[i - 1]) as f32 / 1e6);
    }
    let avg = deltas.iter().sum::<f32>() / deltas.len() as f32;
    let var = deltas.iter().map(|d| (d - avg) * (d - avg)).sum::<f32>() / deltas.len() as f32;
    let fps = if span_ms > 0.0 { (n - 1) as f32 * 1000.0 / span_ms } else { 0.0 };
    Some(FpsSample {
        t: now_ms(),
        fps,
        frames: n as u32,
        avg_frame_ms: avg,
        jitter_ms: var.sqrt(),
    })
}

// ---------- 电源 ----------

fn sample_power() -> PowerSample {
    const BASE: &str = "/sys/class/power_supply/battery";
    let ua = read_i64(&format!("{BASE}/current_now")).unwrap_or(0);
    let uv = read_i64(&format!("{BASE}/voltage_now")).unwrap_or(0);
    PowerSample {
        t: now_ms(),
        capacity: read_i64(&format!("{BASE}/capacity")).unwrap_or(0) as i32,
        current_ua: ua,
        voltage_uv: uv,
        power_w: (ua.abs() as f64 * uv as f64 / 1e12) as f32,
        temp_deci: read_i64(&format!("{BASE}/temp")).unwrap_or(0) as i32,
        status: read_trim(&format!("{BASE}/status")).unwrap_or_default(),
    }
}

// ---------- mem ----------

fn dump_mem() -> MemDump {
    let get = |key: &str| -> u64 {
        fs::read_to_string("/proc/meminfo")
            .ok()
            .and_then(|t| {
                t.lines()
                    .find(|l| l.starts_with(key))
                    .and_then(|l| l.split_whitespace().nth(1).and_then(|v| v.parse().ok()))
            })
            .unwrap_or(0)
    };
    MemDump {
        total_kb: get("MemTotal:"),
        avail_kb: get("MemAvailable:"),
        swap_total_kb: get("SwapTotal:"),
        swap_free_kb: get("SwapFree:"),
    }
}

// ---------- JSONL 追加 ----------

fn append_jsonl(path: &str, json: &str) -> std::io::Result<()> {
    let mut f = fs::OpenOptions::new().create(true).append(true).open(path)?;
    writeln!(f, "{json}")
}

fn write_pidfile(path: &str) {
    if let Some(p) = Path::new(path).parent() {
        let _ = fs::create_dir_all(p);
    }
    let _ = fs::write(path, std::process::id().to_string());
}

fn daemonize(args: &[String]) {
    // 移除 --bg 后再 spawn 自己，避免子进程再次 fork
    let child_args: Vec<&String> = args.iter().filter(|a| a.as_str() != "--bg").collect();
    let _ = Command::new("/proc/self/exe")
        .args(&child_args)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .process_group(0)
        .spawn();
    std::process::exit(0);
}

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.is_empty() {
        eprintln!("usage: scene-rs <fps record|power log|charge guard|dump <fps|power|mem>|stop <pidfile>>");
        std::process::exit(2);
    }
    match (args[0].as_str(), args.get(1).map(String::as_str)) {
        ("dump", Some(what)) => {
            let json: Result<String, serde_json::Error> = match what {
                "fps" => serde_json::to_string(&sample_fps(None).map(|s| (s.t, s.fps)).unwrap_or((0, 0.0))),
                "power" => serde_json::to_string(&sample_power()),
                "mem" => serde_json::to_string(&dump_mem()),
                _ => Ok("{\"error\":\"unknown dump target\"}".to_string()),
            };
            println!("{}", json.unwrap_or_else(|_| "{}".into()));
        }
        ("fps", Some("record")) => {
            if args.iter().any(|a| a == "--bg") {
                daemonize(&args);
            }
            let interval = flag_u64(&args, "--interval-ms").unwrap_or(500);
            let out = flag_str(&args, "--out").unwrap_or_else(|| "/data/local/tmp/scene_fps.jsonl".into());
            let surface = flag_str(&args, "--surface");
            write_pidfile(&format!("{out}.pid"));
            loop {
                if let Some(s) = sample_fps(surface.as_deref()) {
                    if let Ok(j) = serde_json::to_string(&s) {
                        let _ = append_jsonl(&out, &j);
                    }
                }
                thread::sleep(Duration::from_millis(interval));
            }
        }
        ("power", Some("log")) => {
            if args.iter().any(|a| a == "--bg") {
                daemonize(&args);
            }
            let interval = flag_u64(&args, "--interval-ms").unwrap_or(5000);
            let out = flag_str(&args, "--out").unwrap_or_else(|| "/data/local/tmp/scene_power.jsonl".into());
            write_pidfile(&format!("{out}.pid"));
            loop {
                let s = sample_power();
                if let Ok(j) = serde_json::to_string(&s) {
                    let _ = append_jsonl(&out, &j);
                }
                thread::sleep(Duration::from_millis(interval));
            }
        }
        ("charge", Some("guard")) => {
            if args.iter().any(|a| a == "--bg") {
                daemonize(&args);
            }
            let stop_at = flag_u64(&args, "--stop").unwrap_or(90);
            let start_at = flag_u64(&args, "--start").unwrap_or(70);
            let pid = flag_str(&args, "--pid").unwrap_or_else(|| "/data/local/tmp/scene_charge.pid".into());
            write_pidfile(&pid);
            // 探测 input_suspend 节点（MTK 在 battery，高通多在 usb）
            let suspend_path = ["/sys/class/power_supply/battery/input_suspend",
                                "/sys/class/power_supply/usb/input_suspend"]
                .iter().find(|p| std::path::Path::new(p).exists()).map(|s| s.to_string());
            if suspend_path.is_none() {
                eprintln!("no input_suspend node");
                std::process::exit(3);
            }
            let suspend_path = suspend_path.unwrap();
            let cap_path = "/sys/class/power_supply/battery/capacity";
            let mut suspended = false;
            loop {
                if let Some(cap) = read_i64(cap_path) {
                    if !suspended && cap >= stop_at as i64 {
                        let _ = fs::write(&suspend_path, "1");
                        suspended = true;
                    } else if suspended && cap <= start_at as i64 {
                        let _ = fs::write(&suspend_path, "0");
                        suspended = false;
                    }
                }
                thread::sleep(Duration::from_millis(3000));
            }
        }
        ("stop", Some(pidfile)) => {
            if let Ok(pid) = fs::read_to_string(pidfile).map(|s| s.trim().to_string()) {
                if let Ok(pid) = pid.parse::<i32>() {
                    let _ = Command::new("kill").args(["-TERM", &pid.to_string()]).status();
                    let _ = fs::remove_file(pidfile);
                    println!("stopped {pid}");
                }
            }
        }
        _ => {
            eprintln!("unknown command");
            std::process::exit(2);
        }
    }
}

fn flag_u64(args: &[String], name: &str) -> Option<u64> {
    let i = args.iter().position(|a| a == name)?;
    args.get(i + 1)?.parse().ok()
}

fn flag_str(args: &[String], name: &str) -> Option<String> {
    let i = args.iter().position(|a| a == name)?;
    args.get(i + 1).cloned()
}
