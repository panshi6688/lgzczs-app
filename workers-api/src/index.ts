interface Env {
  BUTTONS_KV: KVNamespace;
}

interface ToolItem {
  id: string;
  label: string;
  url: string;
  badge: string | null;
  order: number;
}

interface TabItem {
  name: string;
  order: number;
}

interface ToolGroup {
  id: string;
  name: string;
  tab?: string;
  order: number;
  hints?: string[];
  buttons: ToolItem[];
}

interface ToolConfig {
  groups: ToolGroup[];
  tabs?: TabItem[];
  keywords?: string[];
}

interface AdminPayload {
  password: string;
}

const ADMIN_PASSWORD = 'admin123';
const JWT_SECRET = 'tools-admin-secret-2024';

function base64UrlEncode(str: string): string {
  return btoa(str).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

function base64UrlDecode(str: string): string {
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';
  return atob(str);
}

async function createJWT(payload: Record<string, unknown>): Promise<string> {
  const header = base64UrlEncode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = base64UrlEncode(JSON.stringify({ ...payload, iat: Date.now() }));
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey('raw', encoder.encode(JWT_SECRET), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign('HMAC', key, encoder.encode(`${header}.${body}`));
  const sig = base64UrlEncode(String.fromCharCode(...new Uint8Array(signature)));
  return `${header}.${body}.${sig}`;
}

async function verifyJWT(token: string): Promise<Record<string, unknown> | null> {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey('raw', encoder.encode(JWT_SECRET), { name: 'HMAC', hash: 'SHA-256' }, false, ['verify']);
    const sigBin = Uint8Array.from(atob(parts[2].replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
    const valid = await crypto.subtle.verify('HMAC', key, sigBin, encoder.encode(`${parts[0]}.${parts[1]}`));
    if (!valid) return null;
    return JSON.parse(base64UrlDecode(parts[1]));
  } catch {
    return null;
  }
}

function corsHeaders(): HeadersInit {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  };
}

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders(), 'Content-Type': 'application/json' },
  });
}

async function getConfig(env: Env): Promise<ToolConfig> {
  const raw = await env.BUTTONS_KV.get('config', 'text');
  if (raw) return JSON.parse(raw);
  return { groups: [] };
}

async function saveConfig(env: Env, config: ToolConfig): Promise<void> {
  await env.BUTTONS_KV.put('config', JSON.stringify(config));
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method;
    const path = url.pathname;

    if (method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders() });
    }

    if (path === '/api/buttons' && method === 'GET') {
      const config = await getConfig(env);
      return jsonResponse(config);
    }

    if (path === '/api/admin/login' && method === 'POST') {
      const body: AdminPayload = await request.json();
      if (body.password === ADMIN_PASSWORD) {
        const token = await createJWT({ role: 'admin' });
        return jsonResponse({ token });
      }
      return jsonResponse({ error: '密码错误' }, 401);
    }

    const authHeader = request.headers.get('Authorization') || '';
    const jwtToken = authHeader.replace('Bearer ', '');
    const payload = await verifyJWT(jwtToken);
    if (!payload) {
      return jsonResponse({ error: '未授权' }, 401);
    }

    if (path === '/api/admin/buttons' && method === 'GET') {
      const config = await getConfig(env);
      return jsonResponse(config);
    }

    if (path === '/api/admin/groups' && method === 'POST') {
      const config = await getConfig(env);
      const body = await request.json() as Partial<ToolGroup>;
      const newGroup: ToolGroup = {
        id: body.id || `group_${Date.now()}`,
        name: body.name || '新分组',
        tab: body.tab,
        order: config.groups.length + 1,
        hints: body.hints || [],
        buttons: [],
      };
      config.groups.push(newGroup);
      await saveConfig(env, config);
      return jsonResponse(newGroup, 201);
    }

    const groupMatch = path.match(/^\/api\/admin\/groups\/([^/]+)$/);
    if (groupMatch) {
      const groupId = groupMatch[1];
      const config = await getConfig(env);
      const groupIndex = config.groups.findIndex(g => g.id === groupId);

      if (groupIndex === -1) {
        return jsonResponse({ error: '分组不存在' }, 404);
      }

      if (method === 'PUT') {
        const body = await request.json() as Partial<ToolGroup>;
        config.groups[groupIndex] = { ...config.groups[groupIndex], ...body, tab: body.tab };
        await saveConfig(env, config);
        return jsonResponse(config.groups[groupIndex]);
      }

      if (method === 'DELETE') {
        config.groups.splice(groupIndex, 1);
        await saveConfig(env, config);
        return jsonResponse({ success: true });
      }
    }

    const buttonMatch = path.match(/^\/api\/admin\/groups\/([^/]+)\/buttons\/?$/);
    if (buttonMatch && method === 'POST') {
      const groupId = buttonMatch[1];
      const config = await getConfig(env);
      const group = config.groups.find(g => g.id === groupId);
      if (!group) return jsonResponse({ error: '分组不存在' }, 404);

      const body = await request.json() as Partial<ToolItem>;
      const newButton: ToolItem = {
        id: body.id || `btn_${Date.now()}`,
        label: body.label || '新按钮',
        url: body.url || 'https://',
        badge: body.badge || null,
        order: group.buttons.length + 1,
      };
      group.buttons.push(newButton);
      await saveConfig(env, config);
      return jsonResponse(newButton, 201);
    }

    const buttonItemMatch = path.match(/^\/api\/admin\/groups\/([^/]+)\/buttons\/([^/]+)$/);
    if (buttonItemMatch) {
      const groupId = buttonItemMatch[1];
      const buttonId = buttonItemMatch[2];
      const config = await getConfig(env);
      const group = config.groups.find(g => g.id === groupId);
      if (!group) return jsonResponse({ error: '分组不存在' }, 404);

      const btnIndex = group.buttons.findIndex(b => b.id === buttonId);
      if (btnIndex === -1) return jsonResponse({ error: '按钮不存在' }, 404);

      if (method === 'PUT') {
        const body = await request.json() as Partial<ToolItem>;
        group.buttons[btnIndex] = { ...group.buttons[btnIndex], ...body };
        await saveConfig(env, config);
        return jsonResponse(group.buttons[btnIndex]);
      }

      if (method === 'DELETE') {
        group.buttons.splice(btnIndex, 1);
        await saveConfig(env, config);
        return jsonResponse({ success: true });
      }
    }

    if (path === '/api/admin/reorder' && method === 'PUT') {
      const body = await request.json() as ToolConfig;
      const config: ToolConfig = { groups: body.groups || [] };
      if (body.tabs) config.tabs = body.tabs;
      if (body.keywords) config.keywords = body.keywords;
      await saveConfig(env, config);
      return jsonResponse({ success: true });
    }

    return jsonResponse({ error: 'Not Found' }, 404);
  },
};
