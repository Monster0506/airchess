import * as THREE from 'three';
import { GLTFLoader } from './vendor/three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from './vendor/three/addons/controls/OrbitControls.js';
import { clone } from './vendor/three/addons/utils/SkeletonUtils.js';

const canvas = document.getElementById('threeBoard');
const scene = new THREE.Scene();
scene.background = new THREE.Color('#16231e');
const camera = new THREE.PerspectiveCamera(38, 1, .01, 40);
camera.position.set(0, .55, .72);
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFShadowMap;
renderer.outputColorSpace = THREE.SRGBColorSpace;
const controls = new OrbitControls(camera, canvas);
controls.enableDamping = true;
controls.target.set(0, 0, 0);
controls.minDistance = .45;
controls.maxDistance = 1.5;
controls.maxPolarAngle = Math.PI * .49;
scene.add(new THREE.HemisphereLight(0xf2f0df, 0x38483c, 2));
const key = new THREE.DirectionalLight(0xffedcf, 3.2);
key.position.set(-.5, 1, .4);
key.castShadow = true;
key.shadow.mapSize.set(1024, 1024);
key.shadow.camera.left = -.5;
key.shadow.camera.right = .5;
key.shadow.camera.top = .5;
key.shadow.camera.bottom = -.5;
scene.add(key);
const loader = new GLTFLoader();
const pieces = new Map();
const markers = new THREE.Group();
const pieceGroup = new THREE.Group();
scene.add(markers, pieceGroup);
const squareGeometry = new THREE.PlaneGeometry(.05, .05);
squareGeometry.rotateX(-Math.PI / 2);
const squarePos = square => ({ x: (square.charCodeAt(0) - 97) * .05 - .175, z: (8 - Number(square[1])) * .05 - .175 });
let ready = false;
let arrow = null;
let cameraFlipped = false;
let previousMove = null;
let moveAnimation = null;
let pointerStart = null;
let onSquare;

const modelLoads = [loader.loadAsync('/play/models/board.glb').then(({ scene: model }) => {
  model.traverse(object => { if (object.isMesh) { object.castShadow = true; object.receiveShadow = true; } });
  scene.add(model);
})];
const pieceNames = { king: 'k', queen: 'q', rook: 'r', bishop: 'b', knight: 'n', pawn: 'p' };
for (const color of ['white', 'black']) {
  for (const [name, symbol] of Object.entries(pieceNames)) {
    modelLoads.push(loader.loadAsync(`/play/models/${color}_${name}.glb`)
      .then(({ scene: model }) => pieces.set(`${color[0]}${symbol}`, model)));
  }
}
const modelsReady = Promise.all(modelLoads);
function addMarker(square, color, opacity, type = 'square') {
  const p = squarePos(square);
  const geometry = type === 'dot' ? new THREE.CircleGeometry(.007, 20) : type === 'ring' ? new THREE.RingGeometry(.015, .022, 32) : squareGeometry;
  const marker = new THREE.Mesh(geometry, new THREE.MeshBasicMaterial({ color, transparent: true, opacity, depthWrite: false, side: THREE.DoubleSide }));
  marker.rotation.x = type === 'ring' || type === 'dot' ? -Math.PI / 2 : 0;
  marker.position.set(p.x, .008, p.z);
  marker.userData.square = square;
  markers.add(marker);
}
function update(chess, flipped, selected, legal, last, check, threats) {
  for (const marker of markers.children) {
    if (marker.geometry !== squareGeometry) marker.geometry.dispose();
    marker.material.dispose();
  }
  markers.clear();
  pieceGroup.clear();
  if (cameraFlipped !== flipped) {
    cameraFlipped = flipped;
    camera.position.set(0, .55, flipped ? -.72 : .72);
    controls.target.set(0, 0, 0);
    controls.update();
  }
  for (const square of [last?.slice(0, 2), last?.slice(2, 4)]) if (square) addMarker(square, 0xd9bd78, .42);
  if (selected) addMarker(selected, 0x4f9cda, .5);
  if (check) addMarker(check, 0xd7463c, .57);
  for (const square of legal) addMarker(square, chess.get(square) ? 0x4d3924 : 0x293b2c, chess.get(square) ? .8 : .72, chess.get(square) ? 'ring' : 'dot');
  const board = chess.board();
  const animate = Boolean(last && last !== previousMove);
  previousMove = last;
  const destination = animate ? last.slice(2, 4) : null;
  for (let r = 0; r < 8; r++) for (let f = 0; f < 8; f++) {
    const piece = board[r][f];
    if (!piece) continue;
    const square = String.fromCharCode(97 + f) + (8 - r);
    if (square === destination) continue;
    const base = pieces.get(`${piece.color}${piece.type}`);
    if (!base) continue;
    const p = squarePos(square), model = clone(base);
    model.position.set(p.x, .002, p.z);
    model.traverse(object => { if (object.isMesh) { object.castShadow = true; object.receiveShadow = true; } });
    pieceGroup.add(model);
  }
  if (animate) {
    const to = squarePos(destination), from = squarePos(last.slice(0, 2)), piece = chess.get(destination);
    const base = piece && pieces.get(`${piece.color}${piece.type}`);
    if (base) {
      const model = clone(base);
      model.position.set(from.x, .002, from.z);
      model.traverse(object => { if (object.isMesh) { object.castShadow = true; object.receiveShadow = true; } });
      pieceGroup.add(model);
      moveAnimation = { model, from, to, start: performance.now() };
    }
  }
  if (threats) for (let r = 0; r < 8; r++) for (let f = 0; f < 8; f++) {
    const piece = board[r][f], square = String.fromCharCode(97 + f) + (8 - r);
    if (piece && piece.color !== chess.turn() && chess.isAttacked(square, chess.turn())) addMarker(square, 0xd7463c, .25);
  }
}
export async function init3d(selectSquare) {
  onSquare = selectSquare;
  await modelsReady;
  if (!ready) {
    canvas.addEventListener('pointerdown', event => { pointerStart = { x: event.clientX, y: event.clientY, id: event.pointerId }; });
    canvas.addEventListener('pointerup', event => {
      if (!pointerStart || pointerStart.id !== event.pointerId) return;
      const distance = Math.hypot(event.clientX - pointerStart.x, event.clientY - pointerStart.y);
      pointerStart = null;
      if (distance < 6) pick(event);
    });
    renderer.setAnimationLoop(() => {
      if (canvas.offsetParent === null || document.hidden) return;
      controls.update();
      if (moveAnimation) {
        const progress = Math.min(1, (performance.now() - moveAnimation.start) / 180);
        moveAnimation.model.position.x = THREE.MathUtils.lerp(moveAnimation.from.x, moveAnimation.to.x, progress);
        moveAnimation.model.position.z = THREE.MathUtils.lerp(moveAnimation.from.z, moveAnimation.to.z, progress);
        moveAnimation.model.position.y = .002 + .08 * Math.sin(Math.PI * progress);
        if (progress === 1) moveAnimation = null;
      }
      renderer.render(scene, camera);
    });
    ready = true;
  }
  resize3d();
}
export function resize3d() {
  if (!canvas.clientWidth) return;
  const width = canvas.clientWidth, height = canvas.clientHeight;
  renderer.setSize(width, height, false);
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
}
export function render3d(chess, flipped, selected, legal, last, check, threats) { update(chess, flipped, selected, legal, last, check, threats); }
export function setArrow3d(move) {
  if (arrow) {
    scene.remove(arrow);
    arrow.line.geometry.dispose();
    arrow.cone.geometry.dispose();
    arrow.line.material.dispose();
    arrow.cone.material.dispose();
  }
  arrow = null;
  if (!move || !ready) return;
  const a = squarePos(move.slice(0, 2)), b = squarePos(move.slice(2, 4));
  const start = new THREE.Vector3(a.x, .045, a.z), end = new THREE.Vector3(b.x, .045, b.z), delta = end.clone().sub(start);
  arrow = new THREE.ArrowHelper(delta.clone().normalize(), start, delta.length(), 0xe7ad38, .06, .035);
  scene.add(arrow);
}
function pick(event) {
  const rect = canvas.getBoundingClientRect(), mouse = new THREE.Vector2((event.clientX - rect.left) / rect.width * 2 - 1, -(event.clientY - rect.top) / rect.height * 2 + 1);
  const ray = new THREE.Raycaster();
  ray.setFromCamera(mouse, camera);
  const hit = ray.intersectObjects(markers.children, false)[0];
  let square = hit?.object.userData.square;
  if (!square) {
    const plane = new THREE.Plane(new THREE.Vector3(0, 1, 0), -.005), point = new THREE.Vector3();
    if (!ray.ray.intersectPlane(plane, point)) return;
    const file = Math.floor((point.x + .2) / .05), rank = Math.floor((point.z + .2) / .05);
    if (file < 0 || file > 7 || rank < 0 || rank > 7) return;
    square = String.fromCharCode(97 + file) + (8 - rank);
  }
  onSquare(square);
}
