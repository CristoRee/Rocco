# NBT little-endian minimo (el de Bedrock) para prender experimentos en level.dat.
#   python nbt_le.py <level.dat>
import struct
import sys

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STR, LIST, COMP, IARR, LARR = range(13)


class R:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def take(self, fmt):
        n = struct.calcsize(fmt)
        v = struct.unpack_from("<" + fmt, self.d, self.i)
        self.i += n
        return v[0]

    def string(self):
        n = self.take("H")
        s = self.d[self.i:self.i + n].decode("utf-8", "surrogateescape")
        self.i += n
        return s


def read_payload(r, t):
    if t == BYTE: return (t, r.take("b"))
    if t == SHORT: return (t, r.take("h"))
    if t == INT: return (t, r.take("i"))
    if t == LONG: return (t, r.take("q"))
    if t == FLOAT: return (t, r.take("f"))
    if t == DOUBLE: return (t, r.take("d"))
    if t == BARR:
        n = r.take("i"); v = r.d[r.i:r.i + n]; r.i += n; return (t, v)
    if t == STR: return (t, r.string())
    if t == LIST:
        et = r.take("b"); n = r.take("i")
        return (t, (et, [read_payload(r, et)[1] for _ in range(n)]))
    if t == COMP:
        out = []
        while True:
            ct = r.take("b")
            if ct == END:
                return (t, out)
            name = r.string()
            out.append([name, read_payload(r, ct)])
    if t == IARR:
        n = r.take("i"); return (t, [r.take("i") for _ in range(n)])
    if t == LARR:
        n = r.take("i"); return (t, [r.take("q") for _ in range(n)])
    raise ValueError("tag %d" % t)


def w_string(s):
    b = s.encode("utf-8", "surrogateescape")
    return struct.pack("<H", len(b)) + b


def write_payload(t, v):
    if t == BYTE: return struct.pack("<b", v)
    if t == SHORT: return struct.pack("<h", v)
    if t == INT: return struct.pack("<i", v)
    if t == LONG: return struct.pack("<q", v)
    if t == FLOAT: return struct.pack("<f", v)
    if t == DOUBLE: return struct.pack("<d", v)
    if t == BARR: return struct.pack("<i", len(v)) + v
    if t == STR: return w_string(v)
    if t == LIST:
        et, items = v
        return struct.pack("<bi", et, len(items)) + b"".join(write_payload(et, x) for x in items)
    if t == COMP:
        out = b""
        for name, (ct, cv) in v:
            out += struct.pack("<b", ct) + w_string(name) + write_payload(ct, cv)
        return out + struct.pack("<b", END)
    if t == IARR: return struct.pack("<i", len(v)) + b"".join(struct.pack("<i", x) for x in v)
    if t == LARR: return struct.pack("<i", len(v)) + b"".join(struct.pack("<q", x) for x in v)
    raise ValueError(t)


def set_tag(comp, name, t, v):
    for entry in comp:
        if entry[0] == name:
            entry[1] = (t, v)
            return
    comp.append([name, (t, v)])


def get_tag(comp, name):
    for n, tv in comp:
        if n == name:
            return tv
    return None


path = sys.argv[1]
data = open(path, "rb").read()
version, length = struct.unpack_from("<ii", data, 0)
r = R(data[8:8 + length])
rt = r.take("b")
root_name = r.string()
_, root = read_payload(r, rt)
exp = get_tag(root, "experiments")
if exp is None:
    exp = (COMP, [])
    root.append(["experiments", exp])
comp = exp[1]
set_tag(comp, "gametest", BYTE, 1)
set_tag(comp, "experiments_ever_used", BYTE, 1)
set_tag(comp, "saved_with_toggled_experiments", BYTE, 1)
set_tag(root, "commandsEnabled", BYTE, 1)
body = struct.pack("<b", COMP) + w_string(root_name) + write_payload(COMP, root)
open(path, "wb").write(struct.pack("<ii", version, len(body)) + body)
print("experimentos:", [n for n, _ in comp])
