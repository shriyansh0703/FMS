function Node(){ this.children=[]; this.style={}; this.dataset={}; this.className=''; this._html='';
  this._attrs={}; this.title='';
  this.classList={add(){},remove(){},toggle(){},contains(){return false}}; }
Node.prototype.setAttribute=function(k,v){ this._attrs[k]=v; };
Node.prototype.getAttribute=function(k){ return this._attrs[k]??null; };
Node.prototype.appendChild=function(c){ this.children.push(c); return c; };
Node.prototype.querySelector=function(){ return new Node(); };
Node.prototype.querySelectorAll=function(){ return []; };
Node.prototype.remove=function(){};
Node.prototype.focus=function(){}; Node.prototype.setSelectionRange=function(){};
Object.defineProperty(Node.prototype,'innerHTML',{get(){return this._html},set(v){this._html=v;this.children=[]}});
Object.defineProperty(Node.prototype,'textContent',{get(){return this._html},set(v){this._html=v}});
const _store={};
globalThis.document={
  createElement:()=>new Node(),
  querySelector:s=>_store[s]||(_store[s]=new Node()),
  getElementById:()=>new Node(),
  addEventListener:()=>{},
  activeElement:null
};
globalThis.location={hash:''};
globalThis.history={replaceState:(a,b,h)=>{globalThis.location.hash=String(h||'').replace(/^#/,'')?String(h):'';}};
globalThis.URLSearchParams=globalThis.URLSearchParams||class{constructor(){this.m={}}get(k){return this.m[k]??null}set(k,v){this.m[k]=v}toString(){return Object.entries(this.m).map(([k,v])=>k+'='+v).join('&')}};
globalThis.alert=()=>{}; globalThis.setTimeout=()=>{};
