/* Blockbench desktop plugin. Creates the source model and exports the current edited mesh. */
(function () {
    'use strict';
    const ID = 'setsuna_douli';
    const SIZE = 256;
    const TEXTURE = 'setsuna:textures/cosmetics/bamboo_douli.png';
    let createAction, exportAction;

    function textureData() {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = SIZE;
        const ctx = canvas.getContext('2d');
        const pixels = ctx.createImageData(SIZE, SIZE);
        for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
            const grain = ((x * 73 + y * 37 + x * y * 13) % 19 - 9) * 0.65;
            let color;
            if (x < 192 && y < 192) {
                const dx = x - 96, dy = y - 96;
                const r = Math.hypot(dx, dy);
                const angle = Math.atan2(dy, dx) + Math.PI;
                const across = angle / (2 * Math.PI) * 80;
                const around = r / 2.6;
                const alternating = (Math.floor(across) + Math.floor(around)) % 2;
                const seam = (alternating ? around : across) % 1;
                const shade = seam < 0.13 ? -35 : seam < 0.32 ? 8 : -5;
                color = [192 + shade, 149 + shade, 82 + shade * 0.75];
            } else if (x >= 192 && y < 128) {
                const shade = y % 7 < 2 ? -24 : 8;
                color = [145 + shade, 102 + shade, 48 + shade * 0.6];
            } else if (x >= 192) {
                const shade = (x + Math.floor(y / 3)) % 5 < 2 ? -16 : 6;
                color = [133 + shade, 38 + shade * 0.5, 32 + shade * 0.4];
            } else if (x < 128) {
                const a = Math.floor(x / 4), b = Math.floor(y / 4);
                const shade = (a + b) % 2 ? -13 : 4;
                color = [162 + shade, 120 + shade, 64 + shade * 0.7];
            } else {
                const shade = y % 4 === 0 ? -20 : 0;
                color = [100 + shade, 70 + shade, 37 + shade * 0.5];
            }
            const p = (y * SIZE + x) * 4;
            for (let i = 0; i < 3; i++) pixels.data[p + i] = color[i] + grain;
            pixels.data[p + 3] = 255;
        }
        ctx.putImageData(pixels, 0, 0);
        return canvas.toDataURL('image/png');
    }

    function normal(points) {
        const a = new THREE.Vector3(...points[1]).sub(new THREE.Vector3(...points[0]));
        const b = new THREE.Vector3(...points[2]).sub(new THREE.Vector3(...points[0]));
        return a.cross(b).normalize().toArray();
    }

    function face(mesh, texture, points, uvs, desired) {
        // Match the native .bbmodel precision so reopening does not change face ordering.
        points = points.map(point=>point.map(value=>Math.round(value*100000)/100000));
        uvs = uvs.map(uv=>uv.map(value=>Math.round(value*100000)/100000));
        if (desired && normal(points).reduce((sum, v, i) => sum + v * desired[i], 0) < 0) {
            points = [...points].reverse();
            uvs = [...uvs].reverse();
        }
        const vertices = mesh.addVertices(...points);
        const uv = Object.fromEntries(vertices.map((key, i) => [key, uvs[i]]));
        mesh.addFaces(new MeshFace(mesh, {vertices, uv, texture: texture.uuid}));
    }

    function meshIn(name, group) {
        return new Mesh({name, vertices: {}, origin: [0, 0, 0]}).addTo(group);
    }

    function cylinderPoint(r, y, angle) {
        return [r * Math.cos(angle), y, r * Math.sin(angle)];
    }

    function tube(name, group, texture, path, radius, red = false, sides = 6, triangulate = false) {
        const mesh = meshIn(name, group);
        const rings = path.map((p, i) => {
            const next = path[Math.min(i + 1, path.length - 1)];
            const prev = path[Math.max(i - 1, 0)];
            const tangent = new THREE.Vector3(...next).sub(new THREE.Vector3(...prev)).normalize();
            const axis = Math.abs(tangent.y) < 0.9 ? new THREE.Vector3(0, 1, 0) : new THREE.Vector3(1, 0, 0);
            const u = tangent.clone().cross(axis).normalize();
            const v = tangent.clone().cross(u).normalize();
            return Array.from({length: sides}, (_, j) => new THREE.Vector3(...p)
                .addScaledVector(u, radius * Math.cos(j * 2 * Math.PI / sides))
                .addScaledVector(v, radius * Math.sin(j * 2 * Math.PI / sides)).toArray());
        });
        for (let i = 0; i < rings.length - 1; i++) for (let j = 0; j < sides; j++) {
            const k = (j + 1) % sides;
            const points = [rings[i][j], rings[i][k], rings[i + 1][k], rings[i + 1][j]];
            const desired = points[0].map((v, c) => v - path[i][c]);
            const x = red ? 196 : 200, y = red ? 136 : 8;
            const uv = [[x, y], [x + 8, y], [x + 8, y + 16], [x, y + 16]];
            if (triangulate) {
                for (const indices of [[0,1,2],[0,2,3]]) {
                    face(mesh,texture,indices.map(k=>points[k]),indices.map(k=>uv[k]),desired);
                }
            } else face(mesh, texture, points, uv, desired);
        }
        for (const i of [0, rings.length - 1]) {
            const desired = path[i].map((v, c) => v - path[i === 0 ? 1 : i - 1][c]);
            for (let j = 1; j < sides - 1; j++) {
                const uv = red ? [205, 148] : [212, 12];
                face(mesh, texture, [rings[i][0], rings[i][j], rings[i][j + 1]], [uv, uv, uv], desired);
            }
        }
        return mesh.init();
    }

    async function create() {
        newProject(Formats.free);
        Project.name = 'bamboo_douli';
        Project.model_identifier = 'setsuna:bamboo_douli';
        Project.texture_width = Project.texture_height = SIZE;
        const texture = new Texture({name: 'bamboo_douli.png', width: SIZE, height: SIZE,
            uv_width: SIZE, uv_height: SIZE}).fromDataURL(textureData()).add(false);
        if (!texture.img.complete) await new Promise(resolve => texture.img.addEventListener('load', resolve, {once: true}));
        const root = new Group({name: 'Douli | head pivot', origin: [0, 0, 0]}).init();
        const canopy = new Group({name: 'Woven bamboo canopy', origin: [0, 0, 0]}).addTo(root).init();
        const frame = new Group({name: 'Bound rim and inner frame', origin: [0, 0, 0]}).addTo(root).init();
        const cords = new Group({name: 'Crimson cord and knot', origin: [0, 0, 0]}).addTo(root).init();
        const profile = [[0.36, 13.5], [1.8, 12.85], [3.6, 11.65], [5.5, 10.15], [7.3, 8.8], [8.8, 7.7]];
        const segments = 48;
        for (const underside of [false, true]) {
            const mesh = meshIn(underside ? 'Canopy underside' : 'Woven outer canopy', canopy);
            for (let ring = 0; ring < profile.length - 1; ring++) for (let s = 0; s < segments; s++) {
                const a = s * Math.PI * 2 / segments, b = (s + 1) * Math.PI * 2 / segments;
                const [r0, h0] = profile[ring], [r1, h1] = profile[ring + 1];
                const offset = underside ? -0.14 : 0;
                const points = [cylinderPoint(r0,h0+offset,a), cylinderPoint(r0,h0+offset,b),
                    cylinderPoint(r1,h1+offset,b), cylinderPoint(r1,h1+offset,a)];
                const uv = points.map(([x,,z]) => underside ? [64+x*6.5,224+z*3.4] : [96+x*10.55,96+z*10.55]);
                face(mesh, texture, points, uv, [0, underside ? -1 : 1, 0]);
            }
            for (let s = 0; s < segments; s++) {
                const a = s * Math.PI * 2 / segments, b = (s+1) * Math.PI * 2 / segments;
                const h = underside ? 13.36 : 13.5;
                const points = [[0,h+0.035,0], cylinderPoint(0.36,h,a), cylinderPoint(0.36,h,b)];
                face(mesh, texture, points, points.map(([x,,z])=>[96+x*10.55,96+z*10.55]), [0,underside?-1:1,0]);
            }
            mesh.init();
        }
        const rimPath = Array.from({length: 65}, (_, i) => cylinderPoint(8.79,7.64,i*Math.PI/32));
        tube('Split bamboo bound rim', frame, texture, rimPath, 0.19, false, 8);
        for (let i = 0; i < 16; i++) {
            const a = i * Math.PI / 8;
            tube(`Underside spoke ${i+1}`, frame, texture,
                profile.slice(1).map(([r,h])=>cylinderPoint(r,h-0.23,a)), 0.065, false, 4);
        }
        const band = meshIn('Inner head ring', frame);
        for (let i = 0; i < 48; i++) {
            const a = i*Math.PI/24, b = (i+1)*Math.PI/24;
            const points = [cylinderPoint(5.65,8.23,a),cylinderPoint(5.65,8.23,b),
                cylinderPoint(5.65,8.8,b),cylinderPoint(5.65,8.8,a)];
            face(band,texture,points,[[130,195],[146,195],[146,207],[130,207]], [Math.cos(a),0,Math.sin(a)]);
            face(band,texture,points,[[130,195],[146,195],[146,207],[130,207]], [-Math.cos(a),0,-Math.sin(a)]);
        }
        band.init();
        for (const side of [-1,1]) {
            tube(side < 0 ? 'Left chin cord' : 'Right chin cord', cords, texture,
                [[side*5.55,8.35,0.5],[side*4.5,6.1,1.4],[side*4.4,1.3,2.9],
                 [side*2.2,-0.5,4.35],[0,-0.7,4.5]], 0.085, true);
        }
        tube('Small tie knot', cords, texture,
            [[-0.25,-0.7,4.5],[-0.6,-0.85,4.63],[-0.45,-1.12,4.65],[0,-0.95,4.58],
             [0.45,-1.12,4.65],[0.6,-0.85,4.63],[0.25,-0.7,4.5]], 0.105, true, 6, true);
        tube('Left tie end', cords, texture, [[0,-0.8,4.5],[-0.33,-1.4,4.7],[-0.7,-1.8,4.6]],0.075,true);
        tube('Right tie end', cords, texture, [[0,-0.8,4.5],[0.45,-1.35,4.7],[0.7,-1.6,4.55]],0.075,true);
        Canvas.updateAll();
        unselectAllElements();
        Preview.selected.camera.position.set(28,23,32);
        Preview.selected.controls.target.set(0,6.5,0);
        Preview.selected.controls.update();
        return {meshes: Mesh.all.length, faces: Mesh.all.reduce((sum,m)=>sum+Object.keys(m.faces).length,0)};
    }

    function exportTo(projectRoot) {
        if (!Project || Format.id !== 'free') throw new Error('Open the editable bamboo_douli.bbmodel first.');
        if (Cube.all.some(cube=>cube.export !== false)) throw new Error('Convert the exportable cubes to meshes before exporting.');
        if (Project.texture_width !== SIZE || Project.texture_height !== SIZE) {
            throw new Error('Keep the model UV dimensions at 256 by 256 pixels.');
        }
        const texture = Texture.all.find(t=>t.name==='bamboo_douli.png');
        if (!texture) throw new Error('The bamboo_douli.png texture is missing.');
        Canvas.updateAll();
        Project.model_3d.updateMatrixWorld(true);
        const inverse = Project.model_3d.matrixWorld.clone().invert();
        const quads = [];
        for (const mesh of Mesh.all) {
            let excluded = mesh.export === false;
            for (let parent = mesh.parent; parent instanceof Group; parent = parent.parent) {
                if (parent.export === false) excluded = true;
            }
            if (excluded) continue;
            const matrix = inverse.clone().multiply(mesh.mesh.matrixWorld);
            for (const f of Object.values(mesh.faces)) {
                if (f.texture === null || f.vertices.length < 3) continue;
                if (f.texture !== texture.uuid) throw new Error('All exported faces must use bamboo_douli.png.');
                const keys = f.getSortedVertices();
                const points = keys.map(key=>new THREE.Vector3(...mesh.vertices[key]).applyMatrix4(matrix).toArray());
                const n = normal(points);
                if (n.every(v=>v===0)) throw new Error('A face has zero area.');
                const vertices = keys.map((key,i)=>[...points[i],f.uv[key][0]/SIZE,f.uv[key][1]/SIZE]);
                if (vertices.length === 3) vertices.push([...vertices[2]]);
                quads.push({vertices,normal:n});
            }
        }
        if (!quads.length) throw new Error('The current model contains no exportable faces.');
        const root = projectRoot.replace(/\\/g,'/').replace(/\/$/,'');
        const sourcePath = root + '/models/cosmetics/bamboo_douli.bbmodel';
        Project.save_path = sourcePath;
        Blockbench.writeFile(sourcePath, {content: Codecs.project.compile({absolute_paths:false})});
        Blockbench.writeFile(root+'/src/main/resources/assets/setsuna/cosmetics/bamboo_douli.json',
            {content:JSON.stringify({format_version:1,texture:TEXTURE,quads})+'\n'});
        Blockbench.writeFile(root+'/src/main/resources/assets/setsuna/textures/cosmetics/bamboo_douli.png',
            {savetype:'image',content:texture.getDataURL()});
        Project.saved = true;
        return {source:sourcePath,meshes:Mesh.all.length,quads:quads.length,texture:TEXTURE};
    }

    Plugin.register(ID, {
        title: 'Setsuna Bamboo Douli', author: 'Setsuna', version:'1.0.0', min_version:'5.0.0',
        description:'Build an editable woven bamboo douli and export headwear resources for Setsuna.',
        variant:'desktop',
        onload() {
            globalThis.SetsunaDouli = {create, exportTo};
            createAction = new Action('setsuna_create_douli', {name:'Create Setsuna bamboo douli', icon:'nature', click:create});
            exportAction = new Action('setsuna_export_douli', {name:'Export Setsuna headwear resources', icon:'save',
                click() {
                    const directory = Blockbench.pickDirectory({title:'Select the Syink-Main project root'});
                    if (directory) {
                        try { exportTo(directory); Blockbench.showQuickMessage('Douli source and client resources saved.'); }
                        catch (error) { Blockbench.showMessageBox({title:'Headwear export failed',message:error.message}); }
                    }
                }});
            MenuBar.addAction(createAction,'tools');
            MenuBar.addAction(exportAction,'tools');
        },
        onunload() { createAction?.delete(); exportAction?.delete(); delete globalThis.SetsunaDouli; }
    });
})();
