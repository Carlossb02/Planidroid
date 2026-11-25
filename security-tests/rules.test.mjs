import { readFileSync } from 'node:fs';
import { test, before, after } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, getDocs, collection, query, where, serverTimestamp, Timestamp } from 'firebase/firestore';
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-planidroid',firestore:{rules:readFileSync(new URL('../firestore.rules',import.meta.url),'utf8')}})});
after(async()=>{await env?.cleanup()});
const user=(uid)=>env.authenticatedContext(uid,{email:`${uid.toLowerCase()}@example.com`,email_verified:true}).firestore();
test('private: owner only, monotonic version',async()=>{
 const a=user('A'), b=user('B'), u=env.unauthenticatedContext().firestore();
 const data={kind:'TASK',payload:'{}',deleted:false,version:1,updatedAt:serverTimestamp()};
 await assertSucceeds(setDoc(doc(a,'users/A/records/one'),data));await assertFails(getDoc(doc(b,'users/A/records/one')));await assertFails(getDoc(doc(u,'users/A/records/one')));await assertFails(setDoc(doc(b,'users/A/records/two'),data));await assertSucceeds(getDoc(doc(a,'users/A/records/one')));await assertFails(setDoc(doc(a,'users/A/records/one'),{...data,version:3}));await assertSucceeds(setDoc(doc(a,'users/A/records/one'),{...data,version:2}));
});
test('shared: invitation, acceptance, revocation, roles and isolation',async()=>{
 const a=user('A'), b=user('B'), c=user('C'), d=user('D'), anon=env.unauthenticatedContext().firestore();
 const id='space1', sp=x=>doc(x,'sharedSpaces',id), mem=(x,email)=>doc(x,'memberships',`${id}~${email}`);const invitation=(email,role)=>({spaceId:id,email,role,status:'pending',invitedBy:'A',acceptedUid:'',expiresAt:Timestamp.fromMillis(Date.now()+86400000),updatedAt:serverTimestamp()});
 await assertSucceeds(setDoc(sp(a),{ownerUid:'A',kind:'task',title:'Shared',body:'Content',version:1,updatedAt:serverTimestamp()}));
 await assertFails(getDoc(sp(b)));await assertFails(getDoc(sp(anon)));await assertFails(setDoc(mem(b,'b@example.com'),invitation('b@example.com','admin')));
 await assertSucceeds(setDoc(mem(a,'b@example.com'),invitation('b@example.com','reader')));await assertSucceeds(getDocs(query(collection(b,'memberships'),where('email','==','b@example.com'))));await assertFails(getDoc(sp(b)));await assertFails(updateDoc(mem(b,'b@example.com'),{status:'accepted',acceptedUid:'B',role:'admin',updatedAt:serverTimestamp()}));
 await assertSucceeds(updateDoc(mem(b,'b@example.com'),{status:'accepted',acceptedUid:'B',updatedAt:serverTimestamp()}));await assertSucceeds(getDoc(sp(b)));await assertFails(updateDoc(sp(b),{title:'bad',version:2,updatedAt:serverTimestamp()}));await assertFails(getDoc(doc(b,'users/A/records/one')));
 await assertSucceeds(setDoc(mem(a,'c@example.com'),invitation('c@example.com','editor')));await assertSucceeds(updateDoc(mem(c,'c@example.com'),{status:'accepted',acceptedUid:'C',updatedAt:serverTimestamp()}));await assertSucceeds(updateDoc(sp(c),{title:'Edited',version:2,updatedAt:serverTimestamp()}));await assertFails(updateDoc(sp(c),{ownerUid:'C',version:3,updatedAt:serverTimestamp()}));await assertFails(setDoc(mem(c,'d@example.com'),{...invitation('d@example.com','reader'),invitedBy:'C'}));
 await assertSucceeds(setDoc(doc(c,'sharedSpaces',id,'comments','c1'),{authorUid:'C',body:'Comment',createdAt:serverTimestamp()}));await assertFails(setDoc(doc(c,'sharedSpaces',id,'comments','c2'),{authorUid:'A',body:'Spoof',createdAt:serverTimestamp()}));await assertFails(getDoc(sp(d)));await assertSucceeds(deleteDoc(mem(a,'b@example.com')));await assertFails(getDoc(sp(b)));
});
