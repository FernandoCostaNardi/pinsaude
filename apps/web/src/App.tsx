import React from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<div>Pin Saúde — em construção</div>} />
      </Routes>
    </BrowserRouter>
  )
}
