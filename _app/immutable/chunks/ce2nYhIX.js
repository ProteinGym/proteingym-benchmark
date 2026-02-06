import{w as j}from"./CNw5quZ4.js";import{b as p}from"./BzFXOTpT.js";/*!
 * Copyright (c) Squirrel Chat et al., All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */function R(e,n){let t=e.slice(0,n).split(/\r\n|\n|\r/g);return[t.length,t.pop().length+1]}function Z(e,n,t){let l=e.split(/\r\n|\n|\r/g),o="",r=(Math.log10(n+1)|0)+1;for(let i=n-1;i<=n+1;i++){let a=l[i-1];a&&(o+=i.toString().padEnd(r," "),o+=":  ",o+=a,o+=`
`,i===n&&(o+=" ".repeat(r+t+2),o+=`^
`))}return o}class u extends Error{line;column;codeblock;constructor(n,t){const[l,o]=R(t.toml,t.ptr),r=Z(t.toml,l,o);super(`Invalid TOML document: ${n}

${r}`,t),this.line=l,this.column=o,this.codeblock=r}}/*!
 * Copyright (c) Squirrel Chat et al., All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */function z(e,n){let t=0;for(;e[n-++t]==="\\";);return--t&&t%2}function b(e,n=0,t=e.length){let l=e.indexOf(`
`,n);return e[l-1]==="\r"&&l--,l<=t?l:-1}function x(e,n){for(let t=n;t<e.length;t++){let l=e[t];if(l===`
`)return t;if(l==="\r"&&e[t+1]===`
`)return t+1;if(l<" "&&l!=="	"||l==="")throw new u("control characters are not allowed in comments",{toml:e,ptr:n})}return e.length}function w(e,n,t,l){let o;for(;(o=e[n])===" "||o==="	"||!t&&(o===`
`||o==="\r"&&e[n+1]===`
`);)n++;return l||o!=="#"?n:w(e,x(e,n),t)}function I(e,n,t,l,o=!1){if(!l)return n=b(e,n),n<0?e.length:n;for(let r=n;r<e.length;r++){let i=e[r];if(i==="#")r=b(e,r);else{if(i===t)return r+1;if(i===l||o&&(i===`
`||i==="\r"&&e[r+1]===`
`))return r}}throw new u("cannot find end of structure",{toml:e,ptr:n})}function A(e,n){let t=e[n],l=t===e[n+1]&&e[n+1]===e[n+2]?e.slice(n,n+3):t;n+=l.length-1;do n=e.indexOf(l,++n);while(n>-1&&t!=="'"&&z(e,n));return n>-1&&(n+=l.length,l.length>1&&(e[n]===t&&n++,e[n]===t&&n++)),n}/*!
 * Copyright (c) Squirrel Chat et al., All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */let M=/^(\d{4}-\d{2}-\d{2})?[T ]?(?:(\d{2}):\d{2}:\d{2}(?:\.\d+)?)?(Z|[-+]\d{2}:\d{2})?$/i;class h extends Date{#n=!1;#t=!1;#e=null;constructor(n){let t=!0,l=!0,o="Z";if(typeof n=="string"){let r=n.match(M);r?(r[1]||(t=!1,n=`0000-01-01T${n}`),l=!!r[2],l&&n[10]===" "&&(n=n.replace(" ","T")),r[2]&&+r[2]>23?n="":(o=r[3]||null,n=n.toUpperCase(),!o&&l&&(n+="Z"))):n=""}super(n),isNaN(this.getTime())||(this.#n=t,this.#t=l,this.#e=o)}isDateTime(){return this.#n&&this.#t}isLocal(){return!this.#n||!this.#t||!this.#e}isDate(){return this.#n&&!this.#t}isTime(){return this.#t&&!this.#n}isValid(){return this.#n||this.#t}toISOString(){let n=super.toISOString();if(this.isDate())return n.slice(0,10);if(this.isTime())return n.slice(11,23);if(this.#e===null)return n.slice(0,-1);if(this.#e==="Z")return n;let t=+this.#e.slice(1,3)*60+ +this.#e.slice(4,6);return t=this.#e[0]==="-"?t:-t,new Date(this.getTime()-t*6e4).toISOString().slice(0,-1)+this.#e}static wrapAsOffsetDateTime(n,t="Z"){let l=new h(n);return l.#e=t,l}static wrapAsLocalDateTime(n){let t=new h(n);return t.#e=null,t}static wrapAsLocalDate(n){let t=new h(n);return t.#t=!1,t.#e=null,t}static wrapAsLocalTime(n){let t=new h(n);return t.#n=!1,t.#e=null,t}}/*!
 * Copyright (c) Squirrel Chat et al., All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */let V=/^((0x[0-9a-fA-F](_?[0-9a-fA-F])*)|(([+-]|0[ob])?\d(_?\d)*))$/,v=/^[+-]?\d(_?\d)*(\.\d(_?\d)*)?([eE][+-]?\d(_?\d)*)?$/,G=/^[+-]?0[0-9_]/,U=/^[0-9a-f]{4,8}$/i,N={b:"\b",t:"	",n:`
`,f:"\f",r:"\r",'"':'"',"\\":"\\"};function D(e,n=0,t=e.length){let l=e[n]==="'",o=e[n++]===e[n]&&e[n]===e[n+1];o&&(t-=2,e[n+=2]==="\r"&&n++,e[n]===`
`&&n++);let r=0,i,a="",c=n;for(;n<t-1;){let f=e[n++];if(f===`
`||f==="\r"&&e[n]===`
`){if(!o)throw new u("newlines are not allowed in strings",{toml:e,ptr:n-1})}else if(f<" "&&f!=="	"||f==="")throw new u("control characters are not allowed in strings",{toml:e,ptr:n-1});if(i){if(i=!1,f==="u"||f==="U"){let s=e.slice(n,n+=f==="u"?4:8);if(!U.test(s))throw new u("invalid unicode escape",{toml:e,ptr:r});try{a+=String.fromCodePoint(parseInt(s,16))}catch{throw new u("invalid unicode escape",{toml:e,ptr:r})}}else if(o&&(f===`
`||f===" "||f==="	"||f==="\r")){if(n=w(e,n-1,!0),e[n]!==`
`&&e[n]!=="\r")throw new u("invalid escape: only line-ending whitespace may be escaped",{toml:e,ptr:r});n=w(e,n)}else if(f in N)a+=N[f];else throw new u("unrecognized escape sequence",{toml:e,ptr:r});c=n}else!l&&f==="\\"&&(r=n-1,i=!0,a+=e.slice(c,r))}return a+e.slice(c,t-1)}function X(e,n,t,l){if(e==="true")return!0;if(e==="false")return!1;if(e==="-inf")return-1/0;if(e==="inf"||e==="+inf")return 1/0;if(e==="nan"||e==="+nan"||e==="-nan")return NaN;if(e==="-0")return l?0n:0;let o=V.test(e);if(o||v.test(e)){if(G.test(e))throw new u("leading zeroes are not allowed",{toml:n,ptr:t});e=e.replace(/_/g,"");let i=+e;if(isNaN(i))throw new u("invalid number",{toml:n,ptr:t});if(o){if((o=!Number.isSafeInteger(i))&&!l)throw new u("integer value cannot be represented losslessly",{toml:n,ptr:t});(o||l===!0)&&(i=BigInt(e))}return i}const r=new h(e);if(!r.isValid())throw new u("invalid value",{toml:n,ptr:t});return r}/*!
 * Copyright (c) Squirrel Chat et al., All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */function K(e,n,t,l){let o=e.slice(n,t),r=o.indexOf("#");r>-1&&(x(e,r),o=o.slice(0,r));let i=o.trimEnd();if(!l){let a=o.indexOf(`
`,i.length);if(a>-1)throw new u("newlines are not allowed in inline tables",{toml:e,ptr:n+a})}return[i,r]}function O(e,n,t,l,o){if(l===0)throw new u("document contains excessively nested structures. aborting.",{toml:e,ptr:n});let r=e[n];if(r==="["||r==="{"){let[c,f]=r==="["?F(e,n,l,o):q(e,n,l,o),s=t?I(e,f,",",t):f;if(f-s&&t==="}"){let d=b(e,f,s);if(d>-1)throw new u("newlines are not allowed in inline tables",{toml:e,ptr:d})}return[c,s]}let i;if(r==='"'||r==="'"){i=A(e,n);let c=D(e,n,i);if(t){if(i=w(e,i,t!=="]"),e[i]&&e[i]!==","&&e[i]!==t&&e[i]!==`
`&&e[i]!=="\r")throw new u("unexpected character encountered",{toml:e,ptr:i});i+=+(e[i]===",")}return[c,i]}i=I(e,n,",",t);let a=K(e,n,i-+(e[i-1]===","),t==="]");if(!a[0])throw new u("incomplete key-value declaration: no value specified",{toml:e,ptr:n});return t&&a[1]>-1&&(i=w(e,n+a[1]),i+=+(e[i]===",")),[X(a[0],e,n,o),i]}/*!
 * Copyright (c) Squirrel Chat et al., All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */let Y=/^[a-zA-Z0-9-_]+[ \t]*$/;function E(e,n,t="="){let l=n-1,o=[],r=e.indexOf(t,n);if(r<0)throw new u("incomplete key-value: cannot find end of key",{toml:e,ptr:n});do{let i=e[n=++l];if(i!==" "&&i!=="	")if(i==='"'||i==="'"){if(i===e[n+1]&&i===e[n+2])throw new u("multiline strings are not allowed in keys",{toml:e,ptr:n});let a=A(e,n);if(a<0)throw new u("unfinished string encountered",{toml:e,ptr:n});l=e.indexOf(".",a);let c=e.slice(a,l<0||l>r?r:l),f=b(c);if(f>-1)throw new u("newlines are not allowed in keys",{toml:e,ptr:n+l+f});if(c.trimStart())throw new u("found extra tokens after the string part",{toml:e,ptr:a});if(r<a&&(r=e.indexOf(t,a),r<0))throw new u("incomplete key-value: cannot find end of key",{toml:e,ptr:n});o.push(D(e,n,a))}else{l=e.indexOf(".",n);let a=e.slice(n,l<0||l>r?r:l);if(!Y.test(a))throw new u("only letter, numbers, dashes and underscores are allowed in keys",{toml:e,ptr:n});o.push(a.trimEnd())}}while(l+1&&l<r);return[o,w(e,r+1,!0,!0)]}function q(e,n,t,l){let o={},r=new Set,i,a=0;for(n++;(i=e[n++])!=="}"&&i;){let c={toml:e,ptr:n-1};if(i===`
`)throw new u("newlines are not allowed in inline tables",c);if(i==="#")throw new u("inline tables cannot contain comments",c);if(i===",")throw new u("expected key-value, found comma",c);if(i!==" "&&i!=="	"){let f,s=o,d=!1,[m,P]=E(e,n-1);for(let y=0;y<m.length;y++){if(y&&(s=d?s[f]:s[f]={}),f=m[y],(d=Object.hasOwn(s,f))&&(typeof s[f]!="object"||r.has(s[f])))throw new u("trying to redefine an already defined value",{toml:e,ptr:n});!d&&f==="__proto__"&&Object.defineProperty(s,f,{enumerable:!0,configurable:!0,writable:!0})}if(d)throw new u("trying to redefine an already defined value",{toml:e,ptr:n});let[_,C]=O(e,P,"}",t-1,l);r.add(_),s[f]=_,n=C,a=e[n-1]===","?n-1:0}}if(a)throw new u("trailing commas are not allowed in inline tables",{toml:e,ptr:a});if(!i)throw new u("unfinished table encountered",{toml:e,ptr:n});return[o,n]}function F(e,n,t,l){let o=[],r;for(n++;(r=e[n++])!=="]"&&r;){if(r===",")throw new u("expected value, found comma",{toml:e,ptr:n-1});if(r==="#")n=x(e,n);else if(r!==" "&&r!=="	"&&r!==`
`&&r!=="\r"){let i=O(e,n-1,"]",t-1,l);o.push(i[0]),n=i[1]}}if(!r)throw new u("unfinished array encountered",{toml:e,ptr:n});return[o,n]}/*!
 * Copyright (c) Squirrel Chat et al., All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */function k(e,n,t,l){let o=n,r=t,i,a=!1,c;for(let f=0;f<e.length;f++){if(f){if(o=a?o[i]:o[i]={},r=(c=r[i]).c,l===0&&(c.t===1||c.t===2))return null;if(c.t===2){let s=o.length-1;o=o[s],r=r[s].c}}if(i=e[f],(a=Object.hasOwn(o,i))&&r[i]?.t===0&&r[i]?.d)return null;a||(i==="__proto__"&&(Object.defineProperty(o,i,{enumerable:!0,configurable:!0,writable:!0}),Object.defineProperty(r,i,{enumerable:!0,configurable:!0,writable:!0})),r[i]={t:f<e.length-1&&l===2?3:l,d:!1,i:0,c:{}})}if(c=r[i],c.t!==l&&!(l===1&&c.t===3)||(l===2&&(c.d||(c.d=!0,o[i]=[]),o[i].push(o={}),c.c[c.i++]=c={t:1,d:!1,i:0,c:{}}),c.d))return null;if(c.d=!0,l===1)o=a?o[i]:o[i]={};else if(l===0&&a)return null;return[i,o,c.c]}function J(e,{maxDepth:n=1e3,integersAsBigInt:t}={}){let l={},o={},r=l,i=o;for(let a=w(e,0);a<e.length;){if(e[a]==="["){let c=e[++a]==="[",f=E(e,a+=+c,"]");if(c){if(e[f[1]-1]!=="]")throw new u("expected end of table declaration",{toml:e,ptr:f[1]-1});f[1]++}let s=k(f[0],l,o,c?2:1);if(!s)throw new u("trying to redefine an already defined table or value",{toml:e,ptr:a});i=s[2],r=s[1],a=f[1]}else{let c=E(e,a),f=k(c[0],r,i,0);if(!f)throw new u("trying to redefine an already defined table or value",{toml:e,ptr:a});let s=O(e,c[1],void 0,n,t);f[1][f[0]]=s[0],a=s[1]}if(a=w(e,a,!0),e[a]&&e[a]!==`
`&&e[a]!=="\r")throw new u("each key-value declaration must be followed by an end-of-line",{toml:e,ptr:a});a=w(e,a)}return l}/*!
 * Copyright (c) Squirrel Chat et al., All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */let L=/^[a-z0-9-_]+$/i;function g(e){let n=typeof e;if(n==="object"){if(Array.isArray(e))return"array";if(e instanceof Date)return"date"}return n}function H(e){for(let n=0;n<e.length;n++)if(g(e[n])!=="object")return!1;return e.length!=0}function T(e){return JSON.stringify(e).replace(/\x7f/g,"\\u007f")}function $(e,n,t,l){if(t===0)throw new Error("Could not stringify the object: maximum object depth exceeded");if(n==="number")return isNaN(e)?"nan":e===1/0?"inf":e===-1/0?"-inf":l&&Number.isInteger(e)?e.toFixed(1):e.toString();if(n==="bigint"||n==="boolean")return e.toString();if(n==="string")return T(e);if(n==="date"){if(isNaN(e.getTime()))throw new TypeError("cannot serialize invalid date");return e.toISOString()}if(n==="object")return Q(e,t,l);if(n==="array")return W(e,t,l)}function Q(e,n,t){let l=Object.keys(e);if(l.length===0)return"{}";let o="{ ";for(let r=0;r<l.length;r++){let i=l[r];r&&(o+=", "),o+=L.test(i)?i:T(i),o+=" = ",o+=$(e[i],g(e[i]),n-1,t)}return o+" }"}function W(e,n,t){if(e.length===0)return"[]";let l="[ ";for(let o=0;o<e.length;o++){if(o&&(l+=", "),e[o]===null||e[o]===void 0)throw new TypeError("arrays cannot contain null or undefined values");l+=$(e[o],g(e[o]),n-1,t)}return l+" ]"}function B(e,n,t,l){if(t===0)throw new Error("Could not stringify the object: maximum object depth exceeded");let o="";for(let r=0;r<e.length;r++)o+=`${o&&`
`}[[${n}]]
`,o+=S(0,e[r],n,t,l);return o}function S(e,n,t,l,o){if(l===0)throw new Error("Could not stringify the object: maximum object depth exceeded");let r="",i="",a=Object.keys(n);for(let c=0;c<a.length;c++){let f=a[c];if(n[f]!==null&&n[f]!==void 0){let s=g(n[f]);if(s==="symbol"||s==="function")throw new TypeError(`cannot serialize values of type '${s}'`);let d=L.test(f)?f:T(f);if(s==="array"&&H(n[f]))i+=(i&&`
`)+B(n[f],t?`${t}.${d}`:d,l-1,o);else if(s==="object"){let m=t?`${t}.${d}`:d;i+=(i&&`
`)+S(m,n[f],m,l-1,o)}else r+=d,r+=" = ",r+=$(n[f],s,l,o),r+=`
`}}return e&&(r||!i)&&(r=r?`[${e}]
${r}`:`[${e}]`),r&&i?`${r}
${i}`:r||i}function ee(e,{maxDepth:n=1e3,numbersAsFloat:t=!1}={}){if(g(e)!=="object")throw new TypeError("stringify can only be called with an object");let l=S(0,e,"",n,t);return l[l.length-1]!==`
`?l+`
`:l}/*!
 * Copyright (c) Squirrel Chat et al., All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */const ne={parse:J,stringify:ee,TomlDate:h,TomlError:u};function te(){const{subscribe:e,set:n}=j([]);async function t(){const l=await fetch(`${p}/datasets-list.json`),{slugs:o}=await l.json(),r=await Promise.all(o.map(async i=>{try{const c=await(await fetch(`${p}/datasets/${i}.toml`)).text(),f=ne.parse(c);return{slug:i,data:f}}catch(a){return console.warn(`Error loading ${i}:`,a),null}}));n(r.filter(i=>i!==null))}return{subscribe:e,load:t}}const re=te();export{re as d};
