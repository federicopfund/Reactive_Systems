// Dark Mode Theme Toggle
document.addEventListener('DOMContentLoaded', function() {
    // ============================================
    // Mobile Menu Toggle
    // ============================================
    const MOBILE_BREAKPOINT = 768; // Match SCSS $breakpoint-md
    const hamburger = document.getElementById('hamburger');
    const navbarMenu = document.getElementById('navbarMenu');
    const mobileOverlay = document.getElementById('mobileOverlay');
    const navLinks = document.querySelectorAll('.navbar-menu .nav-link');

    function toggleMobileMenu() {
        const isActive = hamburger.classList.contains('active');
        
        hamburger.classList.toggle('active');
        navbarMenu.classList.toggle('active');
        mobileOverlay.classList.toggle('active');
        
        // Update ARIA attribute
        hamburger.setAttribute('aria-expanded', !isActive);
        
        // Prevent body scroll when menu is open
        if (!isActive) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = '';
        }
    }

    function closeMobileMenu() {
        hamburger.classList.remove('active');
        navbarMenu.classList.remove('active');
        mobileOverlay.classList.remove('active');
        hamburger.setAttribute('aria-expanded', 'false');
        document.body.style.overflow = '';
    }

    // Toggle menu on hamburger click
    if (hamburger) {
        hamburger.addEventListener('click', toggleMobileMenu);
    }

    // Close menu on overlay click
    if (mobileOverlay) {
        mobileOverlay.addEventListener('click', closeMobileMenu);
    }

    // Close menu when clicking nav links
    navLinks.forEach(link => {
        link.addEventListener('click', () => {
            // Small delay to allow navigation to start
            setTimeout(closeMobileMenu, 150);
        });
    });

    // Close menu on escape key
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && navbarMenu.classList.contains('active')) {
            closeMobileMenu();
        }
    });

    // Handle window resize
    let resizeTimer;
    window.addEventListener('resize', () => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(() => {
            // Close mobile menu if window is resized to desktop
            if (window.innerWidth > MOBILE_BREAKPOINT && navbarMenu.classList.contains('active')) {
                closeMobileMenu();
            }
        }, 250);
    });

    // ============================================
    // Interactive Graph Popup
    // ============================================
    const graphIconTrigger = document.getElementById('graphIconTrigger');
    const graphPopup = document.getElementById('graphPopup');
    const graphInfo = document.getElementById('graphInfo');

    // Datos de los nodos
    const nodes = [
        {
            id: 'responsive',
            label: 'Responsivo',
            icon: '📱',
            x: 300,
            y: 80,
            color: '#4CAF50',
            description: 'El sistema responde de manera oportuna y consistente. La capacidad de respuesta es fundamental para la usabilidad.',
            connections: ['resilient', 'elastic']
        },
        {
            id: 'resilient',
            label: 'Resiliente',
            icon: '🛡️',
            x: 480,
            y: 200,
            color: '#2196F3',
            description: 'El sistema permanece responsivo ante fallos mediante replicación, contención y aislamiento de componentes.',
            connections: ['responsive', 'message']
        },
        {
            id: 'elastic',
            label: 'Elástico',
            icon: '📈',
            x: 120,
            y: 200,
            color: '#FF9800',
            description: 'El sistema se adapta a cargas variables mediante escalado dinámico y distribución de carga.',
            connections: ['responsive', 'message']
        },
        {
            id: 'message',
            label: 'Orientado a Mensajes',
            icon: '💬',
            x: 300,
            y: 320,
            color: '#9C27B0',
            description: 'Los componentes se comunican mediante paso asíncrono de mensajes, creando límites claros.',
            connections: ['resilient', 'elastic']
        }
    ];

    let activeNode = null;

    // Función para crear el grafo
    function createGraph() {
        const svg = document.getElementById('graphSvg');
        const connectionsGroup = document.getElementById('connections');
        const nodesGroup = document.getElementById('nodes');

        // Limpiar contenido previo
        connectionsGroup.innerHTML = '';
        nodesGroup.innerHTML = '';

        // Crear conexiones
        nodes.forEach(node => {
            node.connections.forEach(targetId => {
                const targetNode = nodes.find(n => n.id === targetId);
                if (targetNode && nodes.indexOf(node) < nodes.indexOf(targetNode)) {
                    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
                    line.setAttribute('x1', node.x);
                    line.setAttribute('y1', node.y);
                    line.setAttribute('x2', targetNode.x);
                    line.setAttribute('y2', targetNode.y);
                    line.setAttribute('class', 'connection-line');
                    line.setAttribute('data-from', node.id);
                    line.setAttribute('data-to', targetId);
                    connectionsGroup.appendChild(line);

                    // Añadir flecha en el punto medio
                    const midX = (node.x + targetNode.x) / 2;
                    const midY = (node.y + targetNode.y) / 2;
                    const angle = Math.atan2(targetNode.y - node.y, targetNode.x - node.x);
                    
                    const arrow = document.createElementNS('http://www.w3.org/2000/svg', 'polygon');
                    const size = 8;
                    const points = [
                        [midX, midY - size],
                        [midX + size * 1.5, midY],
                        [midX, midY + size]
                    ].map(([x, y]) => {
                        const rotatedX = midX + (x - midX) * Math.cos(angle) - (y - midY) * Math.sin(angle);
                        const rotatedY = midY + (x - midX) * Math.sin(angle) + (y - midY) * Math.cos(angle);
                        return `${rotatedX},${rotatedY}`;
                    }).join(' ');
                    
                    arrow.setAttribute('points', points);
                    arrow.setAttribute('class', 'connection-arrow');
                    arrow.setAttribute('data-from', node.id);
                    arrow.setAttribute('data-to', targetId);
                    connectionsGroup.appendChild(arrow);
                }
            });
        });

        // Crear nodos
        nodes.forEach(node => {
            const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            group.setAttribute('class', `node-group node-${node.id}`);
            group.setAttribute('data-node-id', node.id);
            group.style.cursor = 'pointer';

            // Círculo del nodo
            const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            circle.setAttribute('cx', node.x);
            circle.setAttribute('cy', node.y);
            circle.setAttribute('r', 40);
            circle.setAttribute('class', 'node-circle');
            circle.setAttribute('fill', node.color);
            circle.setAttribute('stroke', node.color);
            group.appendChild(circle);

            // Icono del nodo
            const icon = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            icon.setAttribute('x', node.x);
            icon.setAttribute('y', node.y + 5);
            icon.setAttribute('class', 'node-icon');
            icon.textContent = node.icon;
            group.appendChild(icon);

            // Texto del nodo
            const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            text.setAttribute('x', node.x);
            text.setAttribute('y', node.y + 60);
            text.setAttribute('class', 'node-text');
            text.textContent = node.label;
            group.appendChild(text);

            // Event listeners
            group.addEventListener('click', () => selectNode(node));
            group.addEventListener('mouseenter', () => highlightConnections(node));
            group.addEventListener('mouseleave', () => clearHighlights());

            nodesGroup.appendChild(group);
        });
    }

    // Función para seleccionar un nodo
    function selectNode(node) {
        activeNode = node;
        
        // Actualizar información
        graphInfo.innerHTML = `
            <strong>${node.icon} ${node.label}</strong>
            <p>${node.description}</p>
            <p style="margin-top: 8px; color: rgba(255, 255, 255, 0.6); font-size: 12px;">
                Conectado con: ${node.connections.map(id => {
                    const connNode = nodes.find(n => n.id === id);
                    return connNode.label;
                }).join(', ')}
            </p>
        `;

        // Activar nodo
        document.querySelectorAll('.node-group').forEach(g => g.classList.remove('active'));
        document.querySelector(`[data-node-id="${node.id}"]`).classList.add('active');
        
        highlightConnections(node);
    }

    // Función para resaltar conexiones
    function highlightConnections(node) {
        document.querySelectorAll('.connection-line, .connection-arrow').forEach(el => {
            const from = el.getAttribute('data-from');
            const to = el.getAttribute('data-to');
            
            if (from === node.id || to === node.id) {
                el.classList.add('active');
            } else {
                el.classList.remove('active');
            }
        });
    }

    // Función para limpiar resaltados
    function clearHighlights() {
        if (!activeNode) {
            document.querySelectorAll('.connection-line, .connection-arrow').forEach(el => {
                el.classList.remove('active');
            });
        }
    }

    // Abrir popup
    if (graphIconTrigger) {
        graphIconTrigger.addEventListener('mouseenter', () => {
            graphPopup.classList.add('active');
            createGraph();
            
            // Mostrar información inicial
            graphInfo.innerHTML = '<p>Haz clic en un nodo para ver su descripción y conexiones</p>';
        });
    }

    // Cerrar popup al hacer clic fuera
    if (graphPopup) {
        graphPopup.addEventListener('click', (e) => {
            if (e.target === graphPopup) {
                graphPopup.classList.remove('active');
                activeNode = null;
            }
        });

        // Cerrar con tecla Escape
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && graphPopup.classList.contains('active')) {
                graphPopup.classList.remove('active');
                activeNode = null;
            }
        });
    }

    // ============================================
    // Theme Toggle
    // ============================================
    const themeToggleMobile = document.getElementById('theme-toggle');
    const themeToggleDesktop = document.getElementById('theme-toggle-desktop');
    const themeIconMobile = document.getElementById('theme-icon');
    const themeIconDesktop = document.getElementById('theme-icon-desktop');
    const htmlElement = document.documentElement;
    
    // Obtener tema actual (ya aplicado por el script inline en head)
    const currentTheme = htmlElement.getAttribute('data-theme') || 'light';
    
    // Sincronizar iconos con el tema actual
    function updateThemeIcons(theme) {
        const icon = theme === 'dark' ? '☀️' : '🌙';
        if (themeIconMobile) themeIconMobile.textContent = icon;
        if (themeIconDesktop) themeIconDesktop.textContent = icon;
    }
    
    updateThemeIcons(currentTheme);
    
    // Función para cambiar el tema
    function toggleTheme(button) {
        const currentTheme = htmlElement.getAttribute('data-theme');
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        
        // Agregar clase de animación a ambos botones
        if (themeToggleMobile) themeToggleMobile.classList.add('switching');
        if (themeToggleDesktop) themeToggleDesktop.classList.add('switching');
        
        // Cambiar tema
        htmlElement.setAttribute('data-theme', newTheme);
        localStorage.setItem('theme', newTheme);
        
        // Actualizar iconos
        updateThemeIcons(newTheme);
        
        // Remover clase de animación
        setTimeout(() => {
            if (themeToggleMobile) themeToggleMobile.classList.remove('switching');
            if (themeToggleDesktop) themeToggleDesktop.classList.remove('switching');
        }, 600);
    }
    
    // Toggle theme con animación para ambos botones
    if (themeToggleMobile) {
        themeToggleMobile.addEventListener('click', function() {
            toggleTheme(this);
        });
    }
    
    if (themeToggleDesktop) {
        themeToggleDesktop.addEventListener('click', function() {
            toggleTheme(this);
        });
    }
    
    // Detectar cambios en preferencia del sistema
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        if (!localStorage.getItem('theme')) {
            const newTheme = e.matches ? 'dark' : 'light';
            htmlElement.setAttribute('data-theme', newTheme);
            updateThemeIcons(newTheme);
        }
    });

    // Smooth scrolling for navigation links
    const links = document.querySelectorAll('a[href^="#"]');
    links.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            const targetId = this.getAttribute('href');
            if (targetId === '#') return;
            
            const targetElement = document.querySelector(targetId);
            if (targetElement) {
                targetElement.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            }
        });
    });

    // Auto-hide alert messages after 5 seconds
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.style.transition = 'opacity 0.5s ease';
            alert.style.opacity = '0';
            setTimeout(() => {
                alert.remove();
            }, 500);
        }, 5000);
    });

    // Form validation enhancement
    const form = document.querySelector('.contact-form');
    if (form) {
        form.addEventListener('submit', function(e) {
            const button = form.querySelector('button[type="submit"]');
            if (button) {
                button.disabled = true;
                button.textContent = 'Enviando...';
                
                // Re-enable button after 3 seconds in case of error
                setTimeout(() => {
                    button.disabled = false;
                    button.textContent = 'Enviar Mensaje';
                }, 3000);
            }
        });

        // Real-time validation feedback
        const inputs = form.querySelectorAll('.form-input');
        inputs.forEach(input => {
            input.addEventListener('blur', function() {
                if (this.value.trim() === '' && this.hasAttribute('required')) {
                    this.classList.add('form-input-error');
                } else {
                    this.classList.remove('form-input-error');
                }
            });

            input.addEventListener('input', function() {
                if (this.classList.contains('form-input-error') && this.value.trim() !== '') {
                    this.classList.remove('form-input-error');
                }
            });
        });
    }

    // Add animation on scroll for principle cards
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry, index) => {
            if (entry.isIntersecting) {
                setTimeout(() => {
                    entry.target.style.opacity = '1';
                    entry.target.style.transform = 'translateY(0)';
                }, index * 100);
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);

    // Observe principle cards and benefit items
    const animatedElements = document.querySelectorAll('.principle-card, .benefit-item');
    animatedElements.forEach(el => {
        el.style.opacity = '0';
        el.style.transform = 'translateY(20px)';
        el.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
        observer.observe(el);
    });

    // Navbar scroll effect
    let lastScroll = 0;
    const navbar = document.querySelector('.navbar');
    
    window.addEventListener('scroll', () => {
        const currentScroll = window.pageYOffset;
        
        if (currentScroll > 100) {
            navbar.style.boxShadow = '0 4px 6px -1px rgba(0, 0, 0, 0.1)';
        } else {
            navbar.style.boxShadow = '0 1px 2px 0 rgba(0, 0, 0, 0.05)';
        }
        
        lastScroll = currentScroll;
    });

    // Add particle effect on hero section (optional enhancement)
    const hero = document.querySelector('.hero');
    if (hero) {
        // Create subtle animated background effect
        hero.style.position = 'relative';
        hero.style.overflow = 'hidden';
    }

    // Portfolio Filter Functionality
    const filterButtons = document.querySelectorAll('.filter-btn');
    const portfolioCards = document.querySelectorAll('.portfolio-card');

    if (filterButtons.length > 0 && portfolioCards.length > 0) {
        // Function to filter portfolio items
        function filterPortfolio(filterValue) {
            portfolioCards.forEach((card, index) => {
                const category = card.getAttribute('data-category');
                
                // Add fade out animation
                card.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
                card.style.opacity = '0';
                card.style.transform = 'scale(0.9)';
                
                setTimeout(() => {
                    if (filterValue === 'all' || category === filterValue) {
                        // Show the card
                        card.style.display = 'block';
                        
                        // Trigger reflow to ensure transition works
                        void card.offsetHeight;
                        
                        // Add fade in animation with staggered delay
                        setTimeout(() => {
                            card.style.opacity = '1';
                            card.style.transform = 'scale(1)';
                        }, index * 50);
                    } else {
                        // Hide the card
                        card.style.display = 'none';
                    }
                }, 300);
            });
        }

        // Add click event to filter buttons
        filterButtons.forEach(button => {
            button.addEventListener('click', function() {
                // Remove active class from all buttons
                filterButtons.forEach(btn => btn.classList.remove('active'));
                
                // Add active class to clicked button
                this.classList.add('active');
                
                // Get the filter value
                const filterValue = this.getAttribute('data-filter');
                
                // Filter portfolio cards
                filterPortfolio(filterValue);
            });
        });

        // Initialize cards with proper styling
        portfolioCards.forEach(card => {
            card.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
        });

        // Add keyboard navigation
        filterButtons.forEach((button, index) => {
            button.addEventListener('keydown', function(e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    this.click();
                } else if (e.key === 'ArrowRight') {
                    e.preventDefault();
                    const nextButton = filterButtons[index + 1] || filterButtons[0];
                    nextButton.focus();
                } else if (e.key === 'ArrowLeft') {
                    e.preventDefault();
                    const prevButton = filterButtons[index - 1] || filterButtons[filterButtons.length - 1];
                    prevButton.focus();
                }
            });
        });
    }

    // Principle Cards Popup Functionality
    const principleCards = document.querySelectorAll('.principle-card');
    
    // Create overlay for mobile
    const overlay = document.createElement('div');
    overlay.className = 'popup-overlay';
    overlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.5);
        z-index: 9998;
        opacity: 0;
        visibility: hidden;
        transition: all 0.3s ease;
    `;
    document.body.appendChild(overlay);
    
    if (principleCards.length > 0) {
        principleCards.forEach(card => {
            const popup = card.querySelector('.principle-popup');
            
            if (popup) {
                // For desktop: show on hover (handled by CSS)
                // For mobile/tablet: toggle on click
                card.addEventListener('click', function(e) {
                    // Check if we're on a touch device
                    if ('ontouchstart' in window || navigator.maxTouchPoints > 0) {
                        e.stopPropagation();
                        
                        // Close all other popups
                        principleCards.forEach(otherCard => {
                            if (otherCard !== card) {
                                const otherPopup = otherCard.querySelector('.principle-popup');
                                if (otherPopup) {
                                    otherPopup.style.opacity = '0';
                                    otherPopup.style.visibility = 'hidden';
                                }
                            }
                        });
                        
                        // Toggle current popup
                        const isVisible = popup.style.opacity === '1';
                        if (isVisible) {
                            popup.style.opacity = '0';
                            popup.style.visibility = 'hidden';
                            overlay.style.opacity = '0';
                            overlay.style.visibility = 'hidden';
                        } else {
                            popup.style.opacity = '1';
                            popup.style.visibility = 'visible';
                            popup.style.transform = 'translate(-50%, -50%)';
                            overlay.style.opacity = '1';
                            overlay.style.visibility = 'visible';
                        }
                    }
                });
                
                // Adjust popup position if it goes off-screen (desktop)
                card.addEventListener('mouseenter', function() {
                    if (!('ontouchstart' in window || navigator.maxTouchPoints > 0)) {
                        setTimeout(() => {
                            const rect = popup.getBoundingClientRect();
                            const viewportWidth = window.innerWidth;
                            
                            // Reset transform first
                            popup.style.left = '50%';
                            popup.style.right = 'auto';
                            popup.style.transform = 'translateX(-50%) translateY(0)';
                            
                            // Recalculate after reset
                            const newRect = popup.getBoundingClientRect();
                            
                            // Check if popup goes off right edge
                            if (newRect.right > viewportWidth - 20) {
                                popup.style.left = 'auto';
                                popup.style.right = '0';
                                popup.style.transform = 'translateY(0)';
                            }
                            // Check if popup goes off left edge
                            else if (newRect.left < 20) {
                                popup.style.left = '0';
                                popup.style.right = 'auto';
                                popup.style.transform = 'translateY(0)';
                            }
                        }, 50);
                    }
                });
            }
        });
        
        // Close popups when clicking overlay or outside on touch devices
        overlay.addEventListener('click', function() {
            principleCards.forEach(card => {
                const popup = card.querySelector('.principle-popup');
                if (popup) {
                    popup.style.opacity = '0';
                    popup.style.visibility = 'hidden';
                }
            });
            overlay.style.opacity = '0';
            overlay.style.visibility = 'hidden';
        });
        
        document.addEventListener('click', function(e) {
            if ('ontouchstart' in window || navigator.maxTouchPoints > 0) {
                if (!e.target.closest('.principle-card')) {
                    principleCards.forEach(card => {
                        const popup = card.querySelector('.principle-popup');
                        if (popup) {
                            popup.style.opacity = '0';
                            popup.style.visibility = 'hidden';
                        }
                    });
                    overlay.style.opacity = '0';
                    overlay.style.visibility = 'hidden';
                }
            }
        });
        
        // Add keyboard accessibility
        principleCards.forEach(card => {
            card.setAttribute('tabindex', '0');
            card.setAttribute('role', 'button');
            card.setAttribute('aria-expanded', 'false');
            
            card.addEventListener('keydown', function(e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    const popup = this.querySelector('.principle-popup');
                    if (popup) {
                        const isVisible = popup.style.opacity === '1';
                        popup.style.opacity = isVisible ? '0' : '1';
                        popup.style.visibility = isVisible ? 'hidden' : 'visible';
                        if (!isVisible) {
                            popup.style.transform = 'translateX(-50%) translateY(0)';
                        }
                        this.setAttribute('aria-expanded', !isVisible);
                    }
                } else if (e.key === 'Escape') {
                    const popup = this.querySelector('.principle-popup');
                    if (popup) {
                        popup.style.opacity = '0';
                        popup.style.visibility = 'hidden';
                        overlay.style.opacity = '0';
                        overlay.style.visibility = 'hidden';
                        this.setAttribute('aria-expanded', 'false');
                    }
                }
            });
        });
    }
});
