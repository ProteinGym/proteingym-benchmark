import{w as D}from"./CNw5quZ4.js";import{b as G}from"./BLmIt0dH.js";/*!
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
 */function R(e,n){let t=e.slice(0,n).split(/\r\n|\n|\r/g);return[t.length,t.pop().length+1]}function j(e,n,t){let i=e.split(/\r\n|\n|\r/g),o="",l=(Math.log10(n+1)|0)+1;for(let r=n-1;r<=n+1;r++){let a=i[r-1];a&&(o+=r.toString().padEnd(l," "),o+=":  ",o+=a,o+=`
`,r===n&&(o+=" ".repeat(l+t+2),o+=`^
`))}return o}class u extends Error{line;column;codeblock;constructor(n,t){const[i,o]=R(t.toml,t.ptr),l=j(t.toml,i,o);super(`Invalid TOML document: ${n}

${l}`,t),this.line=i,this.column=o,this.codeblock=l}}/*!
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
 */function M(e,n){let t=0;for(;e[n-++t]==="\\";);return--t&&t%2}function b(e,n=0,t=e.length){let i=e.indexOf(`
`,n);return e[i-1]==="\r"&&i--,i<=t?i:-1}function T(e,n){for(let t=n;t<e.length;t++){let i=e[t];if(i===`
`)return t;if(i==="\r"&&e[t+1]===`
`)return t+1;if(i<" "&&i!=="	"||i==="")throw new u("control characters are not allowed in comments",{toml:e,ptr:n})}return e.length}function w(e,n,t,i){let o;for(;(o=e[n])===" "||o==="	"||!t&&(o===`
`||o==="\r"&&e[n+1]===`
`);)n++;return i||o!=="#"?n:w(e,T(e,n),t)}function p(e,n,t,i,o=!1){if(!i)return n=b(e,n),n<0?e.length:n;for(let l=n;l<e.length;l++){let r=e[l];if(r==="#")l=b(e,l);else{if(r===t)return l+1;if(r===i||o&&(r===`
`||r==="\r"&&e[l+1]===`
`))return l}}throw new u("cannot find end of structure",{toml:e,ptr:n})}function N(e,n){let t=e[n],i=t===e[n+1]&&e[n+1]===e[n+2]?e.slice(n,n+3):t;n+=i.length-1;do n=e.indexOf(i,++n);while(n>-1&&t!=="'"&&M(e,n));return n>-1&&(n+=i.length,i.length>1&&(e[n]===t&&n++,e[n]===t&&n++)),n}/*!
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
 */let U=/^(\d{4}-\d{2}-\d{2})?[T ]?(?:(\d{2}):\d{2}:\d{2}(?:\.\d+)?)?(Z|[-+]\d{2}:\d{2})?$/i;class h extends Date{#n=!1;#t=!1;#e=null;constructor(n){let t=!0,i=!0,o="Z";if(typeof n=="string"){let l=n.match(U);l?(l[1]||(t=!1,n=`0000-01-01T${n}`),i=!!l[2],i&&n[10]===" "&&(n=n.replace(" ","T")),l[2]&&+l[2]>23?n="":(o=l[3]||null,n=n.toUpperCase(),!o&&i&&(n+="Z"))):n=""}super(n),isNaN(this.getTime())||(this.#n=t,this.#t=i,this.#e=o)}isDateTime(){return this.#n&&this.#t}isLocal(){return!this.#n||!this.#t||!this.#e}isDate(){return this.#n&&!this.#t}isTime(){return this.#t&&!this.#n}isValid(){return this.#n||this.#t}toISOString(){let n=super.toISOString();if(this.isDate())return n.slice(0,10);if(this.isTime())return n.slice(11,23);if(this.#e===null)return n.slice(0,-1);if(this.#e==="Z")return n;let t=+this.#e.slice(1,3)*60+ +this.#e.slice(4,6);return t=this.#e[0]==="-"?t:-t,new Date(this.getTime()-t*6e4).toISOString().slice(0,-1)+this.#e}static wrapAsOffsetDateTime(n,t="Z"){let i=new h(n);return i.#e=t,i}static wrapAsLocalDateTime(n){let t=new h(n);return t.#e=null,t}static wrapAsLocalDate(n){let t=new h(n);return t.#t=!1,t.#e=null,t}static wrapAsLocalTime(n){let t=new h(n);return t.#n=!1,t.#e=null,t}}/*!
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
 */let Z=/^((0x[0-9a-fA-F](_?[0-9a-fA-F])*)|(([+-]|0[ob])?\d(_?\d)*))$/,z=/^[+-]?\d(_?\d)*(\.\d(_?\d)*)?([eE][+-]?\d(_?\d)*)?$/,V=/^[+-]?0[0-9_]/,v=/^[0-9a-f]{4,8}$/i,A={b:"\b",t:"	",n:`
`,f:"\f",r:"\r",'"':'"',"\\":"\\"};function L(e,n=0,t=e.length){let i=e[n]==="'",o=e[n++]===e[n]&&e[n]===e[n+1];o&&(t-=2,e[n+=2]==="\r"&&n++,e[n]===`
`&&n++);let l=0,r,a="",c=n;for(;n<t-1;){let f=e[n++];if(f===`
`||f==="\r"&&e[n]===`
`){if(!o)throw new u("newlines are not allowed in strings",{toml:e,ptr:n-1})}else if(f<" "&&f!=="	"||f==="")throw new u("control characters are not allowed in strings",{toml:e,ptr:n-1});if(r){if(r=!1,f==="u"||f==="U"){let s=e.slice(n,n+=f==="u"?4:8);if(!v.test(s))throw new u("invalid unicode escape",{toml:e,ptr:l});try{a+=String.fromCodePoint(parseInt(s,16))}catch{throw new u("invalid unicode escape",{toml:e,ptr:l})}}else if(o&&(f===`
`||f===" "||f==="	"||f==="\r")){if(n=w(e,n-1,!0),e[n]!==`
`&&e[n]!=="\r")throw new u("invalid escape: only line-ending whitespace may be escaped",{toml:e,ptr:l});n=w(e,n)}else if(f in A)a+=A[f];else throw new u("unrecognized escape sequence",{toml:e,ptr:l});c=n}else!i&&f==="\\"&&(l=n-1,r=!0,a+=e.slice(c,l))}return a+e.slice(c,t-1)}function F(e,n,t,i){if(e==="true")return!0;if(e==="false")return!1;if(e==="-inf")return-1/0;if(e==="inf"||e==="+inf")return 1/0;if(e==="nan"||e==="+nan"||e==="-nan")return NaN;if(e==="-0")return i?0n:0;let o=Z.test(e);if(o||z.test(e)){if(V.test(e))throw new u("leading zeroes are not allowed",{toml:n,ptr:t});e=e.replace(/_/g,"");let r=+e;if(isNaN(r))throw new u("invalid number",{toml:n,ptr:t});if(o){if((o=!Number.isSafeInteger(r))&&!i)throw new u("integer value cannot be represented losslessly",{toml:n,ptr:t});(o||i===!0)&&(r=BigInt(e))}return r}const l=new h(e);if(!l.isValid())throw new u("invalid value",{toml:n,ptr:t});return l}/*!
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
 */function H(e,n,t,i){let o=e.slice(n,t),l=o.indexOf("#");l>-1&&(T(e,l),o=o.slice(0,l));let r=o.trimEnd();if(!i){let a=o.indexOf(`
`,r.length);if(a>-1)throw new u("newlines are not allowed in inline tables",{toml:e,ptr:n+a})}return[r,l]}function O(e,n,t,i,o){if(i===0)throw new u("document contains excessively nested structures. aborting.",{toml:e,ptr:n});let l=e[n];if(l==="["||l==="{"){let[c,f]=l==="["?Y(e,n,i,o):K(e,n,i,o),s=t?p(e,f,",",t):f;if(f-s&&t==="}"){let d=b(e,f,s);if(d>-1)throw new u("newlines are not allowed in inline tables",{toml:e,ptr:d})}return[c,s]}let r;if(l==='"'||l==="'"){r=N(e,n);let c=L(e,n,r);if(t){if(r=w(e,r,t!=="]"),e[r]&&e[r]!==","&&e[r]!==t&&e[r]!==`
`&&e[r]!=="\r")throw new u("unexpected character encountered",{toml:e,ptr:r});r+=+(e[r]===",")}return[c,r]}r=p(e,n,",",t);let a=H(e,n,r-+(e[r-1]===","),t==="]");if(!a[0])throw new u("incomplete key-value declaration: no value specified",{toml:e,ptr:n});return t&&a[1]>-1&&(r=w(e,n+a[1]),r+=+(e[r]===",")),[F(a[0],e,n,o),r]}/*!
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
 */let X=/^[a-zA-Z0-9-_]+[ \t]*$/;function E(e,n,t="="){let i=n-1,o=[],l=e.indexOf(t,n);if(l<0)throw new u("incomplete key-value: cannot find end of key",{toml:e,ptr:n});do{let r=e[n=++i];if(r!==" "&&r!=="	")if(r==='"'||r==="'"){if(r===e[n+1]&&r===e[n+2])throw new u("multiline strings are not allowed in keys",{toml:e,ptr:n});let a=N(e,n);if(a<0)throw new u("unfinished string encountered",{toml:e,ptr:n});i=e.indexOf(".",a);let c=e.slice(a,i<0||i>l?l:i),f=b(c);if(f>-1)throw new u("newlines are not allowed in keys",{toml:e,ptr:n+i+f});if(c.trimStart())throw new u("found extra tokens after the string part",{toml:e,ptr:a});if(l<a&&(l=e.indexOf(t,a),l<0))throw new u("incomplete key-value: cannot find end of key",{toml:e,ptr:n});o.push(L(e,n,a))}else{i=e.indexOf(".",n);let a=e.slice(n,i<0||i>l?l:i);if(!X.test(a))throw new u("only letter, numbers, dashes and underscores are allowed in keys",{toml:e,ptr:n});o.push(a.trimEnd())}}while(i+1&&i<l);return[o,w(e,l+1,!0,!0)]}function K(e,n,t,i){let o={},l=new Set,r,a=0;for(n++;(r=e[n++])!=="}"&&r;){let c={toml:e,ptr:n-1};if(r===`
`)throw new u("newlines are not allowed in inline tables",c);if(r==="#")throw new u("inline tables cannot contain comments",c);if(r===",")throw new u("expected key-value, found comma",c);if(r!==" "&&r!=="	"){let f,s=o,d=!1,[m,C]=E(e,n-1);for(let y=0;y<m.length;y++){if(y&&(s=d?s[f]:s[f]={}),f=m[y],(d=Object.hasOwn(s,f))&&(typeof s[f]!="object"||l.has(s[f])))throw new u("trying to redefine an already defined value",{toml:e,ptr:n});!d&&f==="__proto__"&&Object.defineProperty(s,f,{enumerable:!0,configurable:!0,writable:!0})}if(d)throw new u("trying to redefine an already defined value",{toml:e,ptr:n});let[$,P]=O(e,C,"}",t-1,i);l.add($),s[f]=$,n=P,a=e[n-1]===","?n-1:0}}if(a)throw new u("trailing commas are not allowed in inline tables",{toml:e,ptr:a});if(!r)throw new u("unfinished table encountered",{toml:e,ptr:n});return[o,n]}function Y(e,n,t,i){let o=[],l;for(n++;(l=e[n++])!=="]"&&l;){if(l===",")throw new u("expected value, found comma",{toml:e,ptr:n-1});if(l==="#")n=T(e,n);else if(l!==" "&&l!=="	"&&l!==`
`&&l!=="\r"){let r=O(e,n-1,"]",t-1,i);o.push(r[0]),n=r[1]}}if(!l)throw new u("unfinished array encountered",{toml:e,ptr:n});return[o,n]}/*!
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
 */function I(e,n,t,i){let o=n,l=t,r,a=!1,c;for(let f=0;f<e.length;f++){if(f){if(o=a?o[r]:o[r]={},l=(c=l[r]).c,i===0&&(c.t===1||c.t===2))return null;if(c.t===2){let s=o.length-1;o=o[s],l=l[s].c}}if(r=e[f],(a=Object.hasOwn(o,r))&&l[r]?.t===0&&l[r]?.d)return null;a||(r==="__proto__"&&(Object.defineProperty(o,r,{enumerable:!0,configurable:!0,writable:!0}),Object.defineProperty(l,r,{enumerable:!0,configurable:!0,writable:!0})),l[r]={t:f<e.length-1&&i===2?3:i,d:!1,i:0,c:{}})}if(c=l[r],c.t!==i&&!(i===1&&c.t===3)||(i===2&&(c.d||(c.d=!0,o[r]=[]),o[r].push(o={}),c.c[c.i++]=c={t:1,d:!1,i:0,c:{}}),c.d))return null;if(c.d=!0,i===1)o=a?o[r]:o[r]={};else if(i===0&&a)return null;return[r,o,c.c]}function q(e,{maxDepth:n=1e3,integersAsBigInt:t}={}){let i={},o={},l=i,r=o;for(let a=w(e,0);a<e.length;){if(e[a]==="["){let c=e[++a]==="[",f=E(e,a+=+c,"]");if(c){if(e[f[1]-1]!=="]")throw new u("expected end of table declaration",{toml:e,ptr:f[1]-1});f[1]++}let s=I(f[0],i,o,c?2:1);if(!s)throw new u("trying to redefine an already defined table or value",{toml:e,ptr:a});r=s[2],l=s[1],a=f[1]}else{let c=E(e,a),f=I(c[0],l,r,0);if(!f)throw new u("trying to redefine an already defined table or value",{toml:e,ptr:a});let s=O(e,c[1],void 0,n,t);f[1][f[0]]=s[0],a=s[1]}if(a=w(e,a,!0),e[a]&&e[a]!==`
`&&e[a]!=="\r")throw new u("each key-value declaration must be followed by an end-of-line",{toml:e,ptr:a});a=w(e,a)}return i}/*!
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
 */let k=/^[a-z0-9-_]+$/i;function g(e){let n=typeof e;if(n==="object"){if(Array.isArray(e))return"array";if(e instanceof Date)return"date"}return n}function J(e){for(let n=0;n<e.length;n++)if(g(e[n])!=="object")return!1;return e.length!=0}function x(e){return JSON.stringify(e).replace(/\x7f/g,"\\u007f")}function _(e,n,t,i){if(t===0)throw new Error("Could not stringify the object: maximum object depth exceeded");if(n==="number")return isNaN(e)?"nan":e===1/0?"inf":e===-1/0?"-inf":i&&Number.isInteger(e)?e.toFixed(1):e.toString();if(n==="bigint"||n==="boolean")return e.toString();if(n==="string")return x(e);if(n==="date"){if(isNaN(e.getTime()))throw new TypeError("cannot serialize invalid date");return e.toISOString()}if(n==="object")return B(e,t,i);if(n==="array")return Q(e,t,i)}function B(e,n,t){let i=Object.keys(e);if(i.length===0)return"{}";let o="{ ";for(let l=0;l<i.length;l++){let r=i[l];l&&(o+=", "),o+=k.test(r)?r:x(r),o+=" = ",o+=_(e[r],g(e[r]),n-1,t)}return o+" }"}function Q(e,n,t){if(e.length===0)return"[]";let i="[ ";for(let o=0;o<e.length;o++){if(o&&(i+=", "),e[o]===null||e[o]===void 0)throw new TypeError("arrays cannot contain null or undefined values");i+=_(e[o],g(e[o]),n-1,t)}return i+" ]"}function W(e,n,t,i){if(t===0)throw new Error("Could not stringify the object: maximum object depth exceeded");let o="";for(let l=0;l<e.length;l++)o+=`${o&&`
`}[[${n}]]
`,o+=S(0,e[l],n,t,i);return o}function S(e,n,t,i,o){if(i===0)throw new Error("Could not stringify the object: maximum object depth exceeded");let l="",r="",a=Object.keys(n);for(let c=0;c<a.length;c++){let f=a[c];if(n[f]!==null&&n[f]!==void 0){let s=g(n[f]);if(s==="symbol"||s==="function")throw new TypeError(`cannot serialize values of type '${s}'`);let d=k.test(f)?f:x(f);if(s==="array"&&J(n[f]))r+=(r&&`
`)+W(n[f],t?`${t}.${d}`:d,i-1,o);else if(s==="object"){let m=t?`${t}.${d}`:d;r+=(r&&`
`)+S(m,n[f],m,i-1,o)}else l+=d,l+=" = ",l+=_(n[f],s,i,o),l+=`
`}}return e&&(l||!r)&&(l=l?`[${e}]
${l}`:`[${e}]`),l&&r?`${l}
${r}`:l||r}function ee(e,{maxDepth:n=1e3,numbersAsFloat:t=!1}={}){if(g(e)!=="object")throw new TypeError("stringify can only be called with an object");let i=S(0,e,"",n,t);return i[i.length-1]!==`
`?i+`
`:i}/*!
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
 */const ne={parse:q,stringify:ee,TomlDate:h,TomlError:u},te="/manifests.json",ie="https://huggingface.co/datasets/ProteinGym/ProteinGym2.0/resolve/main";function ae(e){return`${ie}/${e}.pgdata`}function le(){const{subscribe:e,set:n}=D([]);async function t(){try{const o=await(await fetch(`${G}${te}`)).json(),l=o.manifests,r=o.commit_hash,a=[];for(const[c,f]of Object.entries(l))try{const s=ne.parse(f);a.push({slug:c,data:s})}catch(s){console.warn(`Error parsing TOML for ${c}:`,s)}n(a),re.set(r)}catch(i){console.error("Error loading datasets from HuggingFace:",i),n([])}}return{subscribe:e,load:t}}const ce=le(),re=D("");export{ce as a,re as d,ae as g};
